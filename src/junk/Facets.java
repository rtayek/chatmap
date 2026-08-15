package junk;

import java.util.LinkedHashSet;
import java.util.Set;

interface Facet {
}

enum Dog3 implements Facet {
	collie, mongrel;
}

enum PC3 implements Facet {
	pc, mac;
}

class Chat3 {
	final Set<Facet> facets = new LinkedHashSet<>();
	String chat = "my dog ate my mac but i still had my pc.";
}

public class Facets {
	public static void main(String[] args) {
		Chat3 chat = new Chat3();
		chat.facets.add(Dog3.collie);
		chat.facets.add(PC3.pc);
		chat.facets.add(PC3.mac);

		System.out.println(chat.facets);
		System.out.println("facet mac: " + chat.facets.contains(PC3.mac));

		for (Facet facet : chat.facets) {
			System.out.println(facet.getClass().getSimpleName() + "." + facet);
		}
		
		System.out.println(chat.chat);
	}
}