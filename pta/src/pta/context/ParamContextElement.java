package pta.context;

import pag.node.GNode;
import pag.node.alloc.Alloc_Node;
import soot.PointsToSet;
import soot.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParamContextElement implements ContextElement{

    private List<Value> params;
    private Alloc_Node base;
    private List<PointsToSet> paramPTS;
    private List<GNode> paramNode;

    /*private static Map<List<Value>,ParamContextElement> universe = new HashMap<>();
    public ParamContextElement(List<Value> params){
        this.params = params;
    }
    public static ParamContextElement v(List<Value> params){
        ParamContextElement rec = universe.get(params);
        if(rec == null){
            universe.put(params,rec = new ParamContextElement(params));
        }
        return rec;
    }
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (( params == null) ? 0 : params.hashCode());
        return result;
    }
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ParamContextElement other = (ParamContextElement) obj;
        if (params == null && other.params != null)
            return false;
        else
            return (params.equals(other.params));
    }
    public String toString() {
        return "ParamContextElement " + params;
    }*/

    private static Map<Alloc_Node,Map<List<Value>,ParamContextElement>> universe = new HashMap<>();
    public ParamContextElement(Alloc_Node base, List<Value> params){
        this.base = base;
        this.params = params;
    }
    public static ParamContextElement v (Alloc_Node base,List<Value> params){
        Map<List<Value>, ParamContextElement> recs = universe.get(base);
        if(recs == null){
            universe.put(base,recs = new HashMap<>());
        }
        ParamContextElement rec = recs.get(params);
        if(rec == null){
            recs.put(params,rec = new ParamContextElement(base,params));
        }
        universe.put(base,recs);
        return rec;
    }
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (( base == null || params == null) ? 0 : base.hashCode() + params.hashCode());
        return result;
    }
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ParamContextElement other = (ParamContextElement) obj;
        if ((params == null && other.params != null) ||(base == null && other.base!=null))
            return false;
        else
            return (params.equals(other.params)&&base.equals(other.base));
    }
    public String toString(){
        return "ParamContextElement" + base+params;
    }

    /*private static Map<Alloc_Node,Map<List<PointsToSet>,ParamContextElement>> universe = new HashMap<>();
    public ParamContextElement(Alloc_Node base, List<PointsToSet> paramPTS){
        this.base = base;
        this.paramPTS = paramPTS;
    }
    public static ParamContextElement v (Alloc_Node base,List<PointsToSet> paramPTS){
        Map<List<PointsToSet>, ParamContextElement> recs = universe.get(base);
        if(recs == null){
            universe.put(base,recs = new HashMap<>());
        }
        ParamContextElement rec = recs.get(paramPTS);
        if(rec == null){
            recs.put(paramPTS,rec = new ParamContextElement(base,paramPTS));
        }
        universe.put(base,recs);
        return rec;
    }
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (( base == null || paramPTS == null) ? 0 : base.hashCode() + paramPTS.hashCode());
        return result;
    }
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ParamContextElement other = (ParamContextElement) obj;
        if ((paramPTS == null && other.paramPTS != null) ||(base == null && other.base!=null))
            return false;
        else
            return (paramPTS.equals(other.paramPTS)&&base.equals(other.base));
    }
    public String toString(){
        return "ParamContextElement" + base;
    }*/

    /*private static Map<List<PointsToSet>,ParamContextElement> universe = new HashMap<>();
    public ParamContextElement(List<PointsToSet> params){
        this.paramPTS = params;
    }
    public static ParamContextElement v(List<PointsToSet> params){
        ParamContextElement rec = universe.get(params);
        if(rec == null){
            universe.put(params,rec = new ParamContextElement(params));
        }
        return rec;
    }
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (( paramPTS == null) ? 0 : paramPTS.hashCode());
        return result;
    }
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ParamContextElement other = (ParamContextElement) obj;
        if (paramPTS == null && other.paramPTS != null)
            return false;
        else
            return (paramPTS.equals(other.paramPTS));
    }
    public String toString() {
        return "ParamContextElement " + paramPTS;
    }*/

    /*private static Map<List<GNode>,ParamContextElement> universe = new HashMap<>();
    public ParamContextElement(List<GNode> paramAlloc){
        this.paramNode = paramAlloc;
    }
    public static ParamContextElement v (List<GNode> paramAlloc){
        ParamContextElement rec = universe.get(paramAlloc);
        if(rec == null){
            universe.put(paramAlloc,rec = new ParamContextElement(paramAlloc));
        }
        return rec;
    }
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (( paramNode == null) ? 0 : paramNode.hashCode());
        return result;
    }
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ParamContextElement other = (ParamContextElement) obj;
        if (paramNode == null && other.paramNode != null)
            return false;
        else
            return (paramNode.equals(other.paramNode));
    }
    public String toString(){
        return "ParamContextElement" +base+paramNode;
    }*/

}
