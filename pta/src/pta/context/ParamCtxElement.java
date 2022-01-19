package pta.context;

import pag.node.GNode;
import pag.node.alloc.Alloc_Node;
import soot.PointsToSet;
import soot.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParamCtxElement implements ContextElement{


    private static Map<List<PointsToSet>,ParamCtxElement> universe = new HashMap<>();

    private List<PointsToSet> paramPts;

    public ParamCtxElement(List<PointsToSet> params){
        this.paramPts = params;
    }

    public static ParamCtxElement v(List<PointsToSet> params){
        ParamCtxElement rec = universe.get(params);
        if(rec == null){
            universe.put(params,rec = new ParamCtxElement(params));
        }
        return rec;
    }



    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((  paramPts == null) ? 0 : paramPts.hashCode());
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
        ParamCtxElement other = (ParamCtxElement) obj;
        if ( paramPts == null && other.paramPts != null)
            return false;
        else
            return (paramPts.equals(other.paramPts));
    }

    public String toString() {
        return "ParamContextElement " + paramPts;
    }
}
