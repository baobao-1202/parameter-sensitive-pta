package pta.context;

import java.util.HashMap;
import java.util.Map;

import soot.Unit;

/**
 * Type based context element in the points to analysis.
 *
 */
public class CallSite implements ContextElement {

	private Unit unit;

	private static Map<Unit, CallSite> universe = new HashMap<Unit, CallSite>();

	public static CallSite v(Unit unit) {
		CallSite ret = universe.get(unit);
		if (ret == null)
			universe.put(unit, ret = new CallSite(unit));
		return ret;
	}

	private CallSite(Unit unit) {
		this.unit = unit;
	}

	public Unit getUnit() {
		return unit;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((unit == null) ? 0 : unit.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CallSite other = (CallSite) obj;
		if (unit == null && other.unit != null)
			return false;
		else
			return unit.equals(other.unit);
	}

	public String toString() {
		return "CallSiteContextElement: " + unit;
	}
}
