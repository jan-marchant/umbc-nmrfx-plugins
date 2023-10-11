package edu.umbc.hhmi.acquisition_plugin;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomResonance;
import org.nmrfx.peaks.ManagedList;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class AcqNode {

    private final AcqTree acqTree;
    private final int id;
    private Atom atom;
    private final Set<AcqTree.Edge> edges=new HashSet<>();
    private ExpDim expDim;
    private Double deltaPPM = null;

    public AcqNode(AcqTree acqTree, int id) {
        this.id=id;
        this.acqTree=acqTree;
    }
    public AcqNode(AcqTree acqTree, int id, ExpDim expDim) {
        this.id=id;
        this.acqTree=acqTree;
        this.expDim=expDim;
    }

    @Override
    public String toString() {
        if (atom==null) {
            return "Node "+id;
        } else {
            return atom.getFullName();
        }
    }

    public double getDeltaPPM (double ppm) {
        if (deltaPPM!=null) {
            return deltaPPM;
        }
        double toReturn=1000;
        for (double candidate : getPPMs()) {
            if (Math.abs(candidate-ppm)<toReturn) {
                toReturn=Math.abs(candidate-ppm);
            }
        }
        return toReturn;
    }

    public ArrayList<Double> getPPMs() {
        ArrayList<Double> ppms=new ArrayList<>();
        int i=0;
        while (atom.getPPM(i)!=null) {
            ppms.add(atom.getPPM(i++).getValue());
        }
        while (atom.getRefPPM(i)!=null) {
            ppms.add(atom.getRefPPM(i++).getValue());
        }
        for (ManagedList list : acqTree.getAcquisition().getManagedLists()){
            ppms.addAll(getPPMs(list));
        }
        for (PeakList list : PeakList.peakLists()) {
            if (!acqTree.getAcquisition().getManagedLists().contains(list)) {
                ppms.addAll(getPPMs(list));
            }
        }
        while(ppms.remove(null));
        return ppms;
    }

    public ArrayList<Double> getPPMs(PeakList peakList) {
        ArrayList<Double> ppms=new ArrayList<>();
        for (int i = 0; i < peakList.getNDim(); i++) {
            for (Peak sPeak : peakList.peaks()) {
                if (((AtomResonance) sPeak.getPeakDim(i).getResonance()).getAtom()==getAtom()) {
                    ppms.add((double) sPeak.getPeakDim(i).getChemShift());
                }
            }
        }
        return ppms;
    }

    public int getId() {
        return id;
    }

    public void setAtom(Atom atom) {
        this.atom = atom;
    }

    public Atom getAtom() {
        return atom;
    }

    public ExpDim getExpDim() {
        return expDim;
    }

    public ExpDim getNextExpDim(boolean forward) {
        return expDim.getNextExpDim(forward);
    }

    public Connectivity.TYPE getNextConType (boolean forward) {
        if (expDim!=null && expDim.getNextCon(forward)!=null) {
            return expDim.getNextCon(forward).type;
        } else {
            return null;
        }
    }

    public ExpDim getNextExpDim() {
        return getNextExpDim(true);
    }

    public void copyTo(AcqNode destination) {
        destination.atom=atom;
        destination.expDim=expDim;
    }

    public void addEdge(AcqTree.Edge edge) {
        edges.add(edge);
    }

    public Collection<AcqTree.Edge> getEdges(ManagedNoeSet noeSet, ManagedNoe noe) {
        return getEdges(true, true, noeSet,noe);
    }

    public Collection<AcqTree.Edge> getEdges(boolean forward, boolean backward, ManagedNoeSet noeSet) {
        return getEdges(forward, backward, noeSet, null);
    }

    public Collection<AcqTree.Edge> getEdges(boolean forward, boolean backward, ManagedNoeSet noeSet, ManagedNoe noe) {
        ArrayList<AcqTree.Edge> toReturn = new ArrayList<>();
        for (AcqTree.Edge edge : edges) {
            if (noeSet==null || edge.noeSet==null || noeSet==edge.noeSet) {
                if (noe==null || noe==edge.noe) {
                    if (forward && edge.node1 == this) {
                        toReturn.add(edge);
                    }
                    if (backward && edge.node2 == this) {
                        toReturn.add(edge);
                    }
                }
            }
        }
        return toReturn;
    }

    public Collection<AcqTree.Edge> getEdges(boolean forward, ManagedNoeSet noeSet) {
        return getEdges(forward,!forward,noeSet);
    }

    public Collection<AcqNode> getNodes(boolean forward, ManagedNoeSet noeSet) {
        return acqTree.getNodes(this,forward,noeSet);
    }

    public void setExpDim(ExpDim expDim) {
        this.expDim = expDim;
    }

    public Set<AcqTree.Edge> getEdgeSet() {
        return edges;
    }

    public void resetDeltaPPM() {
        deltaPPM = null;
    }
}
