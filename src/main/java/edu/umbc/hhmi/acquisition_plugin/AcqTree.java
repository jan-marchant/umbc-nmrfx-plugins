package edu.umbc.hhmi.acquisition_plugin;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.constraints.ManagedNoe;

import java.util.*;

public class AcqTree {

    public static class Edge {
        public AcqNode node1;
        public AcqNode node2;
        public double weight;
        public ManagedNoeSet noeSet;
        public ManagedNoe noe;

        public Edge(AcqNode node1, AcqNode node2, double weight, ManagedNoeSet noeSet, ManagedNoe noe) {
            this.node1 =node1;
            this.node2=node2;
            this.weight=weight;
            this.noeSet=noeSet;
            this.noe=noe;
            node1.addEdge(this);
            node2.addEdge(this);
        }

        public AcqNode getNode() {
            return node1;
        }

        public ExpDim getExpDim() {
            return getNode().getExpDim();
        }

        public AcqNode getNode(boolean forward) {
            if (forward) {return node2;} else {return node1;}
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;

            if (!(o instanceof Edge))
                return false;

            if (((Edge) o).node1 == node1 && ((Edge) o).node2 == node2 && ((Edge) o).noeSet==noeSet && ((Edge) o).noe==noe) {
                //TODO: consider this behavior
                weight=(((Edge) o).weight);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return node1.getId()*node2.getId();
        }
    }
    private final ObservableSet<Edge> edges= FXCollections.observableSet(new HashSet<>());

    public ArrayList<AcqNode> nodes = new ArrayList<>();
    private final HashMap<Atom, List<AcqNode>> atomNodeMap = new HashMap<>();
    private final HashMap<ExpDim, ObservableList<AcqNode>> expDimNodeMap = new HashMap<>();
    private final Set<ManagedNoeSet> noeSets = new HashSet<>();
    private final AcqNode firstNode;
    private final AcqNode lastNode;
    private final Acquisition acquisition;

    public AcqTree(Acquisition acquisition) {
        firstNode=addNode();
        lastNode=addNode();
        this.acquisition=acquisition;
    }

    public Acquisition getAcquisition() {
        return acquisition;
    }

    public AcqNode addNode() {
        int id = nodes.size();
        AcqNode node = new AcqNode(this, id);
        nodes.add(node);
        return node;
    }

    public AcqNode addNode(Atom atom, ExpDim expDim) {
        List<AcqNode> nodeList;
        if (!atomNodeMap.containsKey(atom)) {
            nodeList = new ArrayList<>();
            atomNodeMap.put(atom,nodeList);
        } else {
            nodeList = atomNodeMap.get(atom);
        }
        for (AcqNode node : nodeList) {
            if (node.getExpDim()==expDim) {
                return node;
            }
        }
        AcqNode node = new AcqNode(this, nodes.size(), expDim);
        nodes.add(node);
        nodeList.add(node);
        expDimNodeMap.putIfAbsent(expDim,FXCollections.observableArrayList());
        expDimNodeMap.get(expDim).add(node);
        return node;
    }

    public void removeNode(AcqNode node) {
        Atom atom=node.getAtom();
        ExpDim expDim=node.getExpDim();
        // leave in nodes list or need to reIndex
        // nodes.remove(node);
        node.setAtom(null);
        node.setExpDim(null);
        node.getEdgeSet().clear();
        if (atom!=null) {
            atomNodeMap.get(atom).remove(node);
        }
        if (expDim!=null) {
            expDimNodeMap.get(expDim).remove(node);
        }
    }

    public ExpDim smallestExpDim() {
        try {
            return expDimNodeMap.entrySet().stream()
                    .min((entry1, entry2) -> entry1.getValue().size() > entry2.getValue().size() ? 1 : -1).get().getKey();
        } catch (Exception e) {
            System.out.println("Error getting smallest expDim");
            return null;
        }
    }

    public Collection<AcqNode> getStartingNodes() {
        return getNodes(smallestExpDim());
    }

    public void addNoeSet (ManagedNoeSet noeSet) {
        if (!noeSets.contains(noeSet)) {
            if (noeSet == null) {
                System.out.println("null set");
            } else {
                noeSets.add(noeSet);
                if (acquisition.isSampleLoaded()) {
                    initializeNoeSet(noeSet);
                }
            }
        }
    }

    public void initializeNoeSet(ManagedNoeSet noeSet) {
        for (ManagedNoe noe : noeSet.getConstraints()) {
            addNoeToTree(noeSet, noe);
        }
        noeSet.getConstraints().addListener((ListChangeListener.Change<? extends ManagedNoe> c) -> {
            while (c.next()) {
                for (ManagedNoe addedNoe : c.getAddedSubList()) {
                    addNoeToTree(noeSet, addedNoe);
                }
            }
        });
    }

    public void initializeAllNoeSets() {
        for (ManagedNoeSet noeSet : noeSets) {
            initializeNoeSet(noeSet);
        }
    }

    public Collection<AcqNode> getNodes() {
        return this.nodes;
    }

    public Collection<AcqNode> getNodes(Atom atom) {
        return atomNodeMap.get(atom);
    }

    public Collection<AcqNode> getNodes(ExpDim expDim) {
        return expDimNodeMap.get(expDim);
    }

    public AcqNode getNode(ExpDim expDim, Atom atom) {
        try {
            for (AcqNode node : getNodes(atom)) {
                if (node.getExpDim() == expDim) {
                    return node;
                }
            }
        } catch (Exception ignore) {}
        return null;
    }

    public Collection<AcqNode> getNodes(AcqNode node) {
        return getNodes(node,true,true,null);
    }

    public Collection<AcqNode> getNodes(AcqNode node, boolean forward, ManagedNoeSet noeSet) {
        return getNodes(node,forward,!forward,noeSet);
    }

    public Collection<AcqNode> getNodes(AcqNode node, boolean forward, boolean backward, ManagedNoeSet noeSet) {
        ArrayList<AcqNode> toReturn = new ArrayList<>();
        for (Edge edge : getEdges(forward,backward,node,noeSet)) {
            toReturn.add(edge.getNode(forward));
        }
        return toReturn;
    }


    public void addEdge(AcqNode iNode, AcqNode jNode, double weight) {
        addEdge(iNode,jNode,weight,null,null);
    }

    public void addEdge(AcqNode iNode, AcqNode jNode, double weight, ManagedNoeSet noeSet, ManagedNoe noe) {
        edges.add(new Edge(iNode, jNode, weight, noeSet, noe));
    }

    public void addLeadingEdge (AcqNode iNode) {
        addEdge(firstNode,iNode,1.0);
    }

    public void addTrailingEdge (AcqNode iNode, double weight) {
        addEdge(iNode,lastNode,weight);
    }

    public ObservableSet<Edge> getEdges() {
        return edges;
    }

    public Collection<Edge> getEdges(boolean forward, boolean backward, AcqNode node, ManagedNoeSet noeSet) {
        return node.getEdges(forward,backward,noeSet);
    }
    public Collection<Edge> getForwardEdges(AcqNode node) {
        return getEdges(true,false,node,null);
    }

    public Collection<Edge> getBackwardEdges(AcqNode node) {
        return getEdges(false,true,node,null);
    }

    public AcqNode getFirstNode() {
        return firstNode;
    }

    public AcqNode getLastNode() {
        return lastNode;
    }

    public void addNoeToTree(ManagedNoeSet noeSet, ManagedNoe noe) {
        for (ExpDim expDim : acquisition.getExperiment().expDims) {
            if (expDim.getNextCon()!=null && (expDim.getNextCon().type==Connectivity.TYPE.NOE)) {
                //HashMap<ExpDim,Integer> dimMap = ((ManagedList) noe.peak.getPeakList()).getDimMap();
                //PeakDim peakDim1=noe.getPeakDim1();
                //PeakDim peakDim2=noe.getPeakDim2();
                ////fixme: this is not appropriate for SpatialSetGroups with more than one atom (i.e. ambiguous peak assignments)
                Atom atom1=noe.spg1.getAnAtom();
                Atom atom2=noe.spg2.getAnAtom();
                //fixme: add check for null peakDims
                //Atom atom1=((AtomResonance) peakDim1.getResonance()).getAtom();
                //Atom atom2=((AtomResonance) peakDim2.getResonance()).getAtom();
                //todo: do we need resonances in the noe?
                //Atom atom1=noe.getResonance1().getAtom();
                //Atom atom2=noe.getResonance2().getAtom();
                double intensity = noe.getIntensity()/noe.getScale();
                AcqNode node1 = getNode(expDim,atom1);
                AcqNode node2 = getNode(expDim.getNextExpDim(),atom2);
                if (node1!=null &&node2!=null) {
                    double weight = intensity * acquisition.getSample().getAtomFraction(node1.getAtom());
                    addEdge(node1, node2, weight, noeSet,noe);
                }

                node1 = getNode(expDim,atom2);
                node2 = getNode(expDim.getNextExpDim(),atom1);
                if (node1!=null &&node2!=null) {
                    double weight = intensity * acquisition.getSample().getAtomFraction(node1.getAtom());
                    addEdge(node1, node2, weight, noeSet,noe);
                }
            }
        }
    }

    public List<HashMap<ExpDim, Edge>> getPathEdgesMiddleOut(Edge firstEdge, boolean forward, AcqNode startNode, AcqNode currentNode, HashMap<ExpDim,
            Edge> currentPath, ArrayList<HashMap<ExpDim, Edge>> paths, ManagedNoeSet noeSet) {
        if (startNode==null) {
            for (AcqNode startingNode : getStartingNodes()) {
                getPathEdgesMiddleOut(null, forward, startingNode, startingNode, new HashMap<>(), paths, noeSet);
            }
            return paths;
        }
        if (currentNode == getFirstNode() && startNode!=getFirstNode()) {
            if (currentPath.size() == acquisition.getExperiment().getSize()) {
                paths.add(currentPath);
            }
            return paths;
        }
        if (currentNode == getLastNode()) {
            currentNode=startNode;
            forward=false;
        }
        HashMap<ExpDim, Edge> nextPath = new HashMap<>();
        for (ExpDim expDim : currentPath.keySet()) {
            nextPath.put(expDim, currentPath.get(expDim));
        }
        for (Edge edge : currentNode.getEdges(forward, noeSet)) {
            if (firstEdge!=null && edge!=firstEdge) {
                continue;
            }
            if (edge.getExpDim()!=null) {
                nextPath.put(edge.getExpDim(), edge);
            }
            AcqNode nextNode = edge.getNode(forward);
            getPathEdgesMiddleOut(null, forward, startNode, nextNode, nextPath, paths, noeSet);
        }
        return paths;
    }


    public HashMap<ExpDim, ObservableList<AcqNode>> getPossiblePathNodes(AcqNode pickedNode) {
        HashMap<ExpDim, ObservableList<AcqNode>> possibleNodes = new HashMap<>();
        for (ExpDim expDim : acquisition.getExperiment().expDims) {
            expDimNodeMap.get(expDim).forEach(AcqNode::resetDeltaPPM);
            possibleNodes.putIfAbsent(expDim, FXCollections.observableArrayList());
            possibleNodes.get(expDim).addAll(expDimNodeMap.get(expDim));
        }
        if (pickedNode!=null) {
            List<HashMap<ExpDim, AcqNode>> paths = getPossiblePaths(pickedNode, pickedNode, true, new HashMap<>(), new ArrayList<>());
            HashMap<ExpDim, List<AcqNode>> seenNodes = new HashMap<>();
            for (HashMap<ExpDim, AcqNode> path : paths) {
                for (ExpDim expDim : acquisition.getExperiment().expDims) {
                    seenNodes.putIfAbsent(expDim, new ArrayList<>());
                    seenNodes.get(expDim).add(path.get(expDim));
                }
            }
            for (ExpDim expDim : acquisition.getExperiment().expDims) {
                try {
                    possibleNodes.get(expDim).retainAll(seenNodes.get(expDim));
                } catch (Exception e) {
                    possibleNodes.get(expDim).clear();
                }
            }
        }
        return possibleNodes;
    }

    public List<HashMap<ExpDim, AcqNode>> getPossiblePaths(AcqNode startNode,AcqNode currentNode,boolean forward,HashMap<ExpDim, AcqNode> currentPath,List<HashMap<ExpDim, AcqNode>> paths) {
        if (currentNode == getFirstNode()) {
            if (currentPath.size() == acquisition.getExperiment().getSize()) {
                paths.add(currentPath);
            }
            return paths;
        }
        if (currentNode == getLastNode()) {
            currentNode=startNode;
            forward=false;
        }
        if (currentNode.getExpDim()!=null) {
            currentPath.put(currentNode.getExpDim(), currentNode);
        }
        HashMap<ExpDim, AcqNode> nextPath = new HashMap<>();
        for (ExpDim expDim : currentPath.keySet()) {
            nextPath.put(expDim, currentPath.get(expDim));
        }
        Collection<AcqNode> nextNodes;
        if (currentNode.getNextConType(forward)==Connectivity.TYPE.NOE) {
            //TODO: add option to filter on nearby NOEs (e.g. from trial structure).
            nextNodes=expDimNodeMap.get(currentNode.getNextExpDim(forward));
        } else {
            nextNodes=currentNode.getNodes(forward, null);
        }
        for (AcqNode nextNode : nextNodes) {
            getPossiblePaths(startNode, nextNode, forward, nextPath, paths);
        }

        return paths;
    }
}
