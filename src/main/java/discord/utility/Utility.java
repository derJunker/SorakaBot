package discord.utility;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Utility {
	/**
	 * this method makes a string with alternating casing e.g.: ThIs MeThOd.....
	 * it doesn't work 100% perfect if the first letter is not alphabetical but it works otherwise
	 * @param text the source-text it should transform
	 * @return returns the changed text
	 */
	public static String toAltCase(String text){
		//get the first letter to check if it is upper or lower-case
		char firstChar = text.charAt(0);
		//this var keeps track if the next letter should be upperCase or not(/lowercase)
		boolean toUpper = !Character.isUpperCase(firstChar);

		//going through the text and alternate between lower and upper case, if the char is in the alphabet
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if(Character.isAlphabetic(c)) {
				//making the letter upper/lower case
				if (toUpper) {
					text = text.substring(0, i) + Character.toUpperCase(c) + text.substring(i + 1);
				} else {
					text = text.substring(0, i) + Character.toLowerCase(c) + text.substring(i + 1);
				}
				//make the next letter the other case
				toUpper = !toUpper;
			}
		}
		//return the changed text
		return text;
	}

	/**
	 * it creates a/b with multiset semantics so if the object t1 is twice in a but once in b then it will be once in the difference
	 * @param a the first list
	 * @param b the second list
	 * @param <T> the type of object in the lists
	 * @return returns the difference a/b
	 */
	public static <T> List<T> listDifference(List<T> a, List<T> b){
		List<T> aCopy = new ArrayList<>(a);
		b.forEach(aCopy::remove);
		return aCopy;
	}

	/**
	 * reverses a list
	 * @param list the list which should be reversed, this list stays the same when method gets called
	 * @param <T> -
	 * @return the reversed list
	 */
	public static <T> List<T> reverseList(List<T> list){
		List<T> reversed = new LinkedList<>();
		for(int i = list.size()-1; i > -1; i--){
			reversed.add(list.get(i));
		}
		return reversed;
	}

	/**
	 * this method gets the keys of a map
	 * @param map the map
	 * @param <T> the type of the object
	 * @return returns all the keys of the map
	 */
	public static <T, V> List<T> getKeys(Map<T, V> map){
		final List<T> list = new LinkedList<>();
		map.forEach((key, value) -> list.add(key));
		return list;
	}
}
