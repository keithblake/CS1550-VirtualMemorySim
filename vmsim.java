import java.io.*;
import java.util.*;

public class vmsim {

	public static void main(String[] args) throws FileNotFoundException {
		// handle command line stuff
		String alg = "";
		String trace = "";
		int numframes = -1;
		int refresh = -1;

		// abort if wrong # of args
		if (args.length != 5 && args.length != 7) {
			System.out.println("Error: Incorrect Number of Arguments.");
			System.exit(1);
		}

		if (args.length == 5) {
			numframes = Integer.parseInt(args[1]);
			alg = args[3];
			trace = args[4];
			refresh = -1;
		} else if (args.length == 7) {
			numframes = Integer.parseInt(args[1]);
			alg = args[3];
			refresh = Integer.parseInt(args[5]);
			trace = args[6];
		}

		if (alg.equalsIgnoreCase("opt")) 
			opt(numframes, trace);
		else if (alg.equalsIgnoreCase("clock"))
			clock(numframes, trace);
		else if (alg.equalsIgnoreCase("aging"))
			aging(numframes, refresh, trace);
		else if (alg.equalsIgnoreCase("lru"))
			lru(numframes, trace);
	}

	public static void clock(int numframes, String trace) throws FileNotFoundException {
		int pageFaults = 0;
		int memoryAccesses = 0;
		int diskWrites = 0;
		// to keep track of clock position
		int clock = 0;
		// array for our frames to represent physical memory
		int[] frames = new int[numframes];
		// this will be our page table
		Hashtable<Integer, PageTableEntry>  pt = new Hashtable<Integer, PageTableEntry>();

		//initialize our page table
		for (int i = 0; i < Math.pow(1024, 2); i++) 
			pt.put(i,  new PageTableEntry());

		File f = new File(trace);
		Scanner scan = new Scanner(f);
		int fr = 0;

		// now the meat of the algorithm
		while (scan.hasNextLine()) {
			memoryAccesses++;
			String[] line = scan.nextLine().split(" ");
			// get the page number for this address
			int pagenum = (int) (Long.decode("0X" + line[0])/32000);
			PageTableEntry cur = pt.get(pagenum);
			// update the pte w/ info we read in
			cur.index = pagenum;
			cur.address = line[0];
			cur.referenced = true;
			if (line[1].equalsIgnoreCase("W"))
				cur.dirty = true;

			// if valid is true page is already in memory
			if (cur.valid == true) {
				continue;
			} else {	// else we have a page fault
				pageFaults++;
				if (fr < numframes) {	// if we have compulsory fault
					// we can just put our page in memory in the next frame
					frames[fr] = pagenum;
					// update the page table entry
					cur.valid = true;
					cur.frame = fr;
					fr++;
				} else {	// if we make it down here we have to do the actual clock part
					boolean done = false;
					int evict = 0;
					while (done == false) {
						if (clock == frames.length)
							clock = 0;
						// looking for an unreferenced entry
						if (pt.get(frames[clock]).referenced == false) {
							// if we find one, we have found the frame we want to evict
							evict = frames[clock];
							done = true;
						} else {	// the current page is referenced
							// so we will switch it to unreferenced
							pt.get(frames[clock]).referenced = false;
						}
						clock++;
					}
					// once we make it down here we have found what we want to evict, we just
					// need to actually evict and swap in the new page
					PageTableEntry evictPage = pt.get(evict);
					if (evictPage.dirty)
						diskWrites++;
					// update values for pte going into ram
					cur.frame = evictPage.frame;
					frames[evictPage.frame] = pagenum;
					cur.valid = true;
					// update values for pte being evicted & put it back into the page table
					evictPage.frame = -1;
					evictPage.valid = false;
					evictPage.referenced = false;
					evictPage.dirty = false;
					pt.put(evict, evictPage);
				}
				// put pte going into ram back into page table w/ updated values
				pt.put(pagenum, cur);
			}
		}
		System.out.println("Algorithm: Clock");
		System.out.println("Number of Frames: " + numframes);
		System.out.println("Memory Accesses: " + memoryAccesses);
		System.out.println("Page Faults: " + pageFaults);
		System.out.println("Disk Writes: " + diskWrites);
		scan.close();
	}

	public static void aging(int numframes, int refresh, String trace) throws FileNotFoundException {
		int pageFaults = 0;
		int memoryAccesses = 0;
		int diskWrites = 0;
		int[] frames = new int[numframes];

		// get our page table set up
		Hashtable<Integer, PageTableEntry> pt = new Hashtable<Integer, PageTableEntry>();
		for (int i = 0; i < Math.pow(1024,  2); i++)
			pt.put(i, new PageTableEntry());

		File f = new File(trace);
		Scanner scan = new Scanner(f);
		int fr = 0;

		while (scan.hasNextLine()) {
			memoryAccesses++;
			String[] line = scan.nextLine().split(" ");
			// update referenced counters here
			if (memoryAccesses % refresh == 0) {
				for (int i = 0; i < frames.length; i++) {
					PageTableEntry pte = pt.get(frames[i]);
					if (pte.referenced == false) {
						pte.age >>>= 1;
					} else if (pte.referenced == true) {
						pte.age >>= 1;
						pte.age = pte.age ^ 8;
					}
					pt.put(i,  pte);
				}	
			}
			// get the page number for this address
			int pagenum = (int) (Long.decode("0X" + line[0])/32000);
			PageTableEntry cur = pt.get(pagenum);
			// update the pte w/ info we read in
			cur.index = pagenum;
			cur.address = line[0];
			cur.referenced = true;
			if (line[1].equalsIgnoreCase("W"))
				cur.dirty = true;

			// if valid is true page is already in memory
			if (cur.valid == true) {
				continue;
			} else {	// we have a page fault
				pageFaults++;
				if (fr < numframes) {	// if we have compulsory fault
					// we can just put our page in memory in the next frame
					frames[fr] = pagenum;
					// update the page table entry
					cur.valid = true;
					cur.frame = fr;
					fr++;
				} else {	// if we make it down here we have to do the aging algorithm
					int evict = -1;
					int lowest = 99999999;
					// go thru each frame and find the lowest age, this will be evicted
					int frame = 0;
					for (int i = 0; i < frames.length; i++) {
						PageTableEntry pte = pt.get(frames[i]);
						if (pte.age < lowest) {
							lowest = pte.age;
							evict = pte.index;
							frame = i;
						}
					}
					PageTableEntry evictPage = pt.get(evict);
					if (evictPage.dirty)
						diskWrites++;
					// update values for pte going into ram
					cur.frame = frame;
					frames[frame] = pagenum;
					cur.valid = true;
					// update values for pte being evicted & put it back into the page table
					evictPage.frame = -1;
					evictPage.valid = false;
					evictPage.referenced = false;
					evictPage.dirty = false;
					pt.put(evict, evictPage);
				}
				// put pte going into ram back into page table w/ updated values
				pt.put(pagenum, cur);
			}
		}
		System.out.println("Algorithm: Aging");
		System.out.println("Number of Frames: " + numframes);
		System.out.println("Refresh Rate: " + refresh);
		System.out.println("Memory Accesses: " + memoryAccesses);
		System.out.println("Page Faults: " + pageFaults);
		System.out.println("Disk Writes: " + diskWrites);
		scan.close();
	}

	public static void opt(int numframes, String trace) throws FileNotFoundException {
		int pageFaults = 0;
		int memoryAccesses = 0;
		int diskWrites = 0;
		int[] frames = new int[numframes];

		// create our page table & initialize it
		Hashtable<Integer, PageTableEntry> pt = new Hashtable<Integer, PageTableEntry>();
		for (int i = 0; i < Math.pow(1024, 2); i++)
			pt.put(i, new PageTableEntry());

		// create a second hashtable to look ahead for opt
		Hashtable<Integer, LinkedList<Integer>> ot = new Hashtable<Integer, LinkedList<Integer>>();
		// populate this hashtable
		Scanner scan = new Scanner(new File(trace));
		int count = 0;
		while (scan.hasNextLine()) {
			String[] line = scan.nextLine().split(" ");
			int pagenum = (int) (Long.decode("0X" + line[0])/32000);
			if (ot.get(pagenum) == null) {
				ot.put(pagenum,  new LinkedList<Integer>());
				ot.get(pagenum).add(count);
			}
			else
				ot.get(pagenum).add(count);
			count++;
		}

		// now we have an arraylist for each page containing a list of all the lines
		// the page is found at in the trace file
		scan = new Scanner(new File(trace));
		int fr = 0;
		while (scan.hasNextLine()) {
			memoryAccesses++;
			String[] line = scan.nextLine().split(" ");
			int pagenum = (int) (Long.decode("0X" + line[0])/32000);
			ot.get(pagenum).removeFirst();
			PageTableEntry cur = pt.get(pagenum);
			cur.referenced = true;
			cur.index = pagenum;
			if (line[1].equalsIgnoreCase("W"))
				cur.dirty = true;
			if (cur.valid == true)
				continue;
			else {
				pageFaults++;
				if (fr < numframes) {
					frames[fr] = pagenum;
					cur.valid = true;
					cur.frame = fr;
					fr++;
				} else {
					// if we make it here we need to find the page w/ the longest distance until
					// use and evict it
					int longest = 0;
					int page = 0;
					for (int i = 0; i < frames.length; i++) {
						if (ot.get(frames[i]).isEmpty()) {
							page = frames[i];
							break;
						} else {
							if (ot.get(frames[i]).get(0) > longest) {
								longest = ot.get(frames[i]).get(0);
								page = frames[i];
							}
						}
					}
					PageTableEntry evictPage = pt.get(page);
					if (evictPage.dirty == true)
						diskWrites++;
					// update values for pte going into ram
					cur.frame = evictPage.frame;
					frames[evictPage.frame] = pagenum;
					cur.valid = true;
					// update values for pte being evicted & put it back into the page table
					evictPage.frame = -1;
					evictPage.valid = false;
					evictPage.referenced = false;
					evictPage.dirty = false;
					pt.put(evictPage.index, evictPage);		
				}
				// put pte going into ram back into page table w/ updated values
				pt.put(pagenum, cur);
			}
		}
		System.out.println("Algorithm: Opt");
		System.out.println("Number of Frames: " + numframes);
		System.out.println("Memory Accesses: " + memoryAccesses);
		System.out.println("Page Faults: " + pageFaults);
		System.out.println("Disk Writes: " + diskWrites);
		scan.close();
	}

	public static void lru(int numframes, String trace) throws FileNotFoundException {
		int pageFaults = 0;
		int memoryAccesses = 0;
		int diskWrites = 0;
		int[] frames = new int[numframes];

		// create our page table & initialize it
		Hashtable<Integer, PageTableEntry> pt = new Hashtable<Integer, PageTableEntry>();
		for (int i = 0; i < Math.pow(1024, 2); i++)
			pt.put(i, new PageTableEntry());

		File f = new File(trace);
		Scanner scan = new Scanner(f);
		int fr = 0;
		int count = 0;

		while (scan.hasNextLine()) {
			memoryAccesses++;
			String[] line = scan.nextLine().split(" ");
			// get the page number for this address
			int pagenum = (int) (Long.decode("0X" + line[0])/32000);
			PageTableEntry cur = pt.get(pagenum);
			// update the pte w/ info we read in
			cur.index = pagenum;
			cur.address = line[0];
			cur.referenced = true;
			cur.timestamp = count;
			if (line[1].equalsIgnoreCase("W"))
				cur.dirty = true;

			// if valid is true page is already in memory
			if (cur.valid == true) {
				continue;
			} else {	// else we have a page fault
				pageFaults++;
				if (fr < numframes) {	// if we have compulsory fault
					// we can just put our page in memory in the next frame
					frames[fr] = pagenum;
					// update the page table entry
					cur.valid = true;
					cur.frame = fr;
					fr++;
				} else {	// if we make it down here we have to do the lru part
					int least = -1;
					int evict = -1;
					for (int i = 0; i < frames.length; i++) {
						if (evict == -1) {
							evict = pt.get(frames[i]).index;
							least = pt.get(frames[i]).timestamp;
						} else if (pt.get(frames[i]).timestamp < least) {
							evict = pt.get(frames[i]).index;
							least = pt.get(frames[i]).timestamp;
						}
					}
					// once we make it down here we have found what we want to evict, we just
					// need to actually evict and swap in the new page
					PageTableEntry evictPage = pt.get(evict);
					if (evictPage.dirty)
						diskWrites++;
					// update values for pte going into ram
					cur.frame = evictPage.frame;
					frames[evictPage.frame] = pagenum;
					cur.valid = true;
					// update values for pte being evicted & put it back into the page table
					evictPage.frame = -1;
					evictPage.valid = false;
					evictPage.referenced = false;
					evictPage.dirty = false;
					pt.put(evict, evictPage);
				}
				// put pte going into ram back into page table w/ updated values
				pt.put(pagenum, cur);
			}
		}
		System.out.println("Algorithm: LRU");
		System.out.println("Number of Frames: " + numframes);
		System.out.println("Memory Accesses: " + memoryAccesses);
		System.out.println("Page Faults: " + pageFaults);
		System.out.println("Disk Writes: " + diskWrites);
		scan.close();
	}
}

