package pta.context;

import java.util.Arrays;

import soot.Context;

public class ContextElements implements Context {
	private ContextElement[] array;

	public ContextElement[] getElements() {
		return array;
	}

	public int getLength() {
		return array.length;
	}

	public ContextElements(ContextElement[] array) {
		this.array = array;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(array);
	}

	/**
	 * Return the number of non-null or non-No-Context elements, assuming that
	 * in the array once we see a no-context element, we don't see a context
	 * element.
	 * 
	 * @return
	 */
	public int numContextElements() {
		for (int i = 0; i < array.length; i++) {
			if (array[i] == null)
				return i;
		}

		return array.length;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (getClass() != obj.getClass())
			return false;
		ContextElements other = (ContextElements) obj;

		// if (!Arrays.equals(array, other.array)) return false;

		// custom array equals for context
		// allows for checking of different sized arrays, but represent same
		// context-sensitive heap object
		if (this.array == null || other.array == null)
			return false;

		if (this.numContextElements() != other.numContextElements())
			return false;

		for (int i = 0; i < numContextElements(); i++) {
			Object o1 = this.array[i];
			Object o2 = other.array[i];
			if (!(o1 == null ? o2 == null : o1.equals(o2)))
				return false;
		}

		return true;
	}

	@Override
	public String toString() {
		StringBuilder localStringBuilder = new StringBuilder();
	    localStringBuilder.append('[');
	    for (int i = 0; i < array.length-1; i++){
	    	localStringBuilder.append(array[i]);
	    	localStringBuilder.append(", ");
	    }
	    for (int i = array.length-1; i >= 0;){
	    	localStringBuilder.append(array[i]);
	    	break;
		}
	    localStringBuilder.append(']');
	    return localStringBuilder.toString();
	}
}
