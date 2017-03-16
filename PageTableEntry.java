public class PageTableEntry {
	String address;
	int frame;
	boolean dirty;
	boolean referenced;
	boolean valid;
	int age;
	int index;
	int timestamp;
	
	public PageTableEntry() {
		address = "";
		frame = -1;
		dirty = false;
		referenced = false;
		valid = false;
		age = 0;
		index = -1;
		timestamp = -1;
	}
	
	public String toString() {
		return "Address: " + address + "\nFrame: " + frame + "\nindex: " + index;
	}
}