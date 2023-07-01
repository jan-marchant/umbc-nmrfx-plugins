package edu.umbc.hhmi.acquisition_plugin;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.SpatialSet;
import org.nmrfx.chemistry.search.MNode;
import org.nmrfx.structure.chemistry.JCoupling;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class JCouplingPath extends JCoupling {

    List<Atom> intervening=new ArrayList<>();

    public JCouplingPath(ArrayList<Atom> atoms, LinkedList<MNode> path) {
        super(atomsToSpatialSet(atoms), path.size());
        LinkedList pathCopy = (LinkedList) path.clone();
        pathCopy.removeFirst();
        pathCopy.removeLast();
        for (MNode node : path) {
            intervening.add(node.getAtom());
        }
    }

    static ArrayList<SpatialSet> atomsToSpatialSet (ArrayList<Atom> atoms) {
        ArrayList<SpatialSet> localSets = new ArrayList<SpatialSet>();

        for (Atom atom : atoms) {
            localSets.add(atom.spatialSet);
        }
        return localSets;
    }

    public List<Atom> getIntervening() {
        return intervening;
    }
}
