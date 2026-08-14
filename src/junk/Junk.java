package junk;
import java.util.Set;
import java.util.TreeSet;
public interface Junk {
	TreeSet<Junk> set();

	static TreeSet<Junk> newSet() {
		return new TreeSet<>((left, right) -> {
			int typeComparison = left.getClass().getName().compareTo(right.getClass().getName());
			if (typeComparison != 0) {
				return typeComparison;
			}
			return left.toString().compareTo(right.toString());
		});
	}

	public static void main(String[] args) {
		System.err.println(new Chat().tags);
	}
}
enum Dog implements Junk {
	collie, mongel;

	public TreeSet<Junk> set() {
		return set;
	}

	final TreeSet<Junk> set = Junk.newSet();
}
enum PC implements Junk {
	pc, mac;

	public TreeSet<Junk> set() {
		return set;
	}

	final TreeSet<Junk> set = Junk.newSet();
}
class Chat {
	Chat() {
		tags.add(Dog.collie);
		tags.add(PC.pc);
		tags.add(PC.mac);
	}

	String chat = "my dog at my mac but i still had my pc.";
	Set<Junk> tags = Junk.newSet();
}
