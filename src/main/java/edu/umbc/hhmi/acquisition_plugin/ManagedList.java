package edu.umbc.hhmi.acquisition_plugin;

import javafx.collections.SetChangeListener;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.constraints.Noe;
import org.nmrfx.chemistry.constraints.NoeSet;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.peaks.*;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.project.ProjectBase;


import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

//todo set sample label
public class ManagedList extends PeakList {

    /* Notes:
    ManagedList does not need to implement SaveFrameWriter as it extends PeakList
    and overrides PeakList.writeStar3Header
     */
    static void doStartup() {
        ManagedListSaveframeProcessor managedListSaveFrameProcessor = new ManagedListSaveframeProcessor();
        ProjectBase.addSaveframeProcessor("spectral_managed_peak_list", managedListSaveFrameProcessor);
    }

    //SNR required for picking peak - useful when adding breakthrough labeling percentages
    private double detectionLimit=3;
    private double noise;
    //private Double highestSignal;
    private double pickThreshold() {
        return detectionLimit*noise;//highestSignal;
    }
    //TODO: persist across saves - have to think of how to do for sample, experiment, acquisition anyway
    private HashMap<ExpDim,Integer> dimMap = new HashMap<>();
    private Acquisition acquisition;
    private int ppmSet;
    private int rPpmSet;
    //probably don't need this type - just the noeSet which can be passed by managedListSetup
    public NoeSet2 noeSet=null;
    private Noe2 addedNoe=null;
    //private ManagedPeak addedPeak=null;
    //protected List<ManagedPeak> peaks;

    //initial creation
    public ManagedList(Acquisition acquisition, String name, int ppmSet, int rPpmset, NoeSet2 noeSet, HashMap<ExpDim,Integer> dimMap) {
        super(name, acquisition.getDataset().getNDim());
        this.acquisition = acquisition;
        this.ppmSet = ppmSet;
        this.rPpmSet = rPpmset;
        this.noise = acquisition.getDataset().guessNoiseLevel();
        this.dimMap=dimMap;
        initializeList(acquisition.getDataset());
        setNoeSet(noeSet);
        addPeaks();
        setupListener();
    }

    //Loading from star file
    // fixme: rPpmSet not set on reload
    public ManagedList(String name, int nDim, Acquisition acquisition, HashMap<ExpDim,Integer> dimMap, int listNum) {
        super(name,nDim,listNum);
        this.acquisition=acquisition;
        this.setSampleConditionLabel(acquisition.getCondition().toString());
        this.setSampleLabel(acquisition.getSample().toString());
        this.setSlideable(true);
        //fixme
        //what about ppmSet?
        this.rPpmSet = 0;
        this.noise = acquisition.getDataset().guessNoiseLevel();
        this.dimMap=dimMap;
        initializeList(acquisition.getDataset());
        //noeSet, peaks and listener must follow as they may not be loaded yet
    }

    public void setNoeSet(NoeSet2 noeSet) {
        this.noeSet=noeSet;
        acquisition.getAcqTree().addNoeSet(noeSet);
    }

    public void setupListener() {
        acquisition.getAcqTree().getEdges().addListener((SetChangeListener.Change<? extends AcqTree.Edge> c) -> {
            if (c.wasAdded()) {
                addEdgeToList(c.getElementAdded(),true);
            }
        });
    }

    public void initializeList(Dataset dataset) {
        if (dataset!=null) {
            this.fileName = dataset.getFileName();
            for (int i = 0; i < dataset.getNDim(); i++) {
                int dDim = i;
                SpectralDim sDim = getSpectralDim(i);
                sDim.setDimName(dataset.getLabel(dDim));
                sDim.setSf(dataset.getSf(dDim));
                sDim.setSw(dataset.getSw(dDim));
                sDim.setSize(dataset.getSizeTotal(dDim));
                double minTol = Math.round(100 * 2.0 * dataset.getSw(dDim) / dataset.getSf(dDim) / dataset.getSizeTotal(dDim)) / 100.0;
                double tol = minTol;
                Nuclei nuc = dataset.getNucleus(dDim);
                if (null != nuc) {
                    switch (nuc) {
                        case H1:
                            tol = 0.05;
                            break;
                        case C13:
                            tol = 0.6;
                            break;
                        case N15:
                            tol = 0.2;
                            break;
                        default:
                            tol = minTol;
                    }
                }
                tol = Math.min(tol, minTol);

                sDim.setIdTol(tol);
                sDim.setDataDim(dDim);
                sDim.setNucleus(dataset.getNucleus(dDim).getNumberName());
            }
        }
    }

    @Override
    public ManagedPeak addPeak(Peak pickedPeak) {

        //fixme: what about when getNewPeak called? need to be careful

        //TODO: repick from existing NOEs taking new detection limit into account
        if (pickedPeak.getIntensity()<pickThreshold()) {
            detectionLimit=0.9*pickedPeak.getIntensity(); //*highestSignal/noise;
        }

        pickedPeak.initPeakDimContribs();

        AcqNodeChooser chooser = new AcqNodeChooser(this,pickedPeak);
        chooser.create();
        chooser.showAndWaitAtMouse();
        List<ManagedPeak> addedPeaks=new ArrayList<>();

        if (noeSet!=null) {
            if (addedNoe != null) {
                addedPeaks.addAll(addNoeToList(addedNoe));
            }
            if (addedPeaks.size() > 0) {
                //addedNoe.setPeak(addedPeaks.get(addedPeaks.size() - 1));
                addedNoe.setPeak(addedPeaks.get(addedPeaks.size() - 1));
            } else {
                System.out.println("Error adding NOE " + addedNoe);
                noeSet.getConstraints().remove(addedNoe);
            }
        }
        for (PeakDim peakDim : pickedPeak.peakDims) {
            peakDim.remove();
            if (peakDim.hasMultiplet()) {
                Multiplet multiplet = peakDim.getMultiplet();
            }
        }
        this.unLinkPeak(pickedPeak);
        pickedPeak.markDeleted();
        addedNoe=null;

        if (addedPeaks.size()>0) {
            this.idLast--;
            return addedPeaks.get(addedPeaks.size()-1);
        } else {
            //fixme: risk of idLast getting out of sync here - sometimes return value ignored
            return null;
        }
    }



    public Set<Peak> getMatchingPeaks(Peak searchPeak, Boolean includeSelf) {
        Set<Peak> matchingPeaks;
        matchingPeaks = new HashSet<>();
        Set<Peak> matchOneDimPeaks;
        matchOneDimPeaks = new HashSet<>();
        Set<PeakDim> seenPeakDims;
        seenPeakDims = new HashSet<>();

        Boolean first=true;
        for (PeakDim peakDim : searchPeak.getPeakDims()) {
            for (PeakDim linkedPeakDim : peakDim.getLinkedPeakDims()) {
                //only delete matching peaks from this list
                if (linkedPeakDim.getPeakList()==this) {
                    //Only consider each peakDim once.
                    // If a peak has already matched this peakDim, don't include.
                    // Otherwise issues with diagonal peak matching.
                    // This is a bit naff. fixme
                    if (!seenPeakDims.contains(linkedPeakDim) && !matchOneDimPeaks.contains(linkedPeakDim.getPeak())) {
                        matchOneDimPeaks.add(linkedPeakDim.getPeak());
                        seenPeakDims.add(linkedPeakDim);
                    }
                }
            }
            if (first) {
                matchingPeaks.addAll(matchOneDimPeaks);
                matchOneDimPeaks.clear();
                first=false;
            } else {
                matchingPeaks.retainAll(matchOneDimPeaks);
                matchOneDimPeaks.clear();
            }
        }
        if (!includeSelf) {
            matchingPeaks.remove(searchPeak);
        }
        return matchingPeaks;
    }

    public ArrayList<String> getDetailText() {
        ArrayList<String> detailArray=new ArrayList<>();
        detailArray.add(peaks().size()+" peaks");
        detailArray.add("rPPM Set: "+rPpmSet);
        if (noeSet!=null) {
            Optional<Map.Entry<String, NoeSet2>> optionalEntry = NoeSetup.getProjectNoeSets(acquisition.getProject()).entrySet().stream().filter(ap -> ap.getValue().equals(noeSet)).findFirst();
            if (optionalEntry.isPresent()) {
            detailArray.add("NOE Set: "+optionalEntry.get().getKey());
            }
        }
        return detailArray;
    }

    private void addEdgeToList(AcqTree.Edge edge, boolean check) {
        if (check && edge.noe==addedNoe) {
            return;
        }
        addPeaks(edge);
        return;
    }

    public List<ManagedPeak> addNoeToList(Noe2 noe) {
        List<ManagedPeak> addedPeaks = new ArrayList<>();
        for (AcqNode node : acquisition.getAcqTree().getNodes(noe.spg1.getAnAtom())) {
            for (AcqTree.Edge edge : node.getEdges(noeSet,noe)) {
                addedPeaks.addAll(addPeaks(edge));
            }
        }
        return addedPeaks;
    }

    private List<ManagedPeak> addPeaks() {
        return addPeaks(null);
    }
    private List<ManagedPeak> addPeaks(AcqTree.Edge firstEdge) {
        System.out.println("Adding peaks");
        ArrayList<ManagedPeak> addedPeaks=new ArrayList<>();
        AcqNode startNode;
        if (firstEdge==null) {
            startNode=null;
        } else {
            startNode=firstEdge.getNode();
        }
        List<HashMap<ExpDim, AcqTree.Edge>> paths=acquisition.getAcqTree().getPathEdgesMiddleOut(firstEdge,true,startNode,startNode,new HashMap<>(),new ArrayList<>(), this.noeSet);
        System.out.println("Found paths:");
        System.out.println(paths.size());
        for (HashMap<ExpDim, AcqTree.Edge> path : paths) {
            addedPeaks.add(addPeakFromPath(path));
        }
        return addedPeaks;
    }

    private ManagedPeak addPeakFromPath(HashMap<ExpDim, AcqTree.Edge> path) {
        Double peakIntensity=1.0;
        Set<Noe2> noes=new HashSet<>();
        HashMap<Integer, Atom> atoms=new HashMap<>();
        for (ExpDim expDim : acquisition.getExperiment().expDims) {
            AcqTree.Edge edge=path.get(expDim);
            peakIntensity*=edge.weight;
            if (expDim.isObserved()) {
                atoms.put(dimMap.get(expDim),edge.getNode().getAtom());
            }
            if (expDim.getNextCon()!=null && expDim.getNextCon().type==Connectivity.TYPE.NOE) {
                noes.add(edge.noe);
            }
        }
        ManagedPeak newPeak = new ManagedPeak(this, this.nDim, noes, atoms);
        peaks().add(newPeak);
        this.reIndex();
        newPeak.setIntensity(peakIntensity.floatValue());
        return newPeak;
    }

    public HashMap<ExpDim, Integer> getDimMap() {
        return dimMap;
    }

    public void setAddedNoe(Noe2 addedNoe) {
        this.addedNoe = addedNoe;
    }

    public int getPpmSet() {
        return ppmSet;
    }

    public int getRPpmSet() {
        return rPpmSet;
    }

    public Acquisition getAcquisition() {
        return acquisition;
    }

    public Noe2 getAddedNoe() {
        return addedNoe;
    }

    //better to not write peaklist during "normal" write - instead write at the end so that it can be processed correctly.
    //This doesn't work because PeakWriter still adds all the peaks, just with missing header, and crashes on read
    //@Override
    //public void writeSTAR3Header(Writer chan) throws IOException {
    //}

    //Use a slightly different category so that it is not read in by the default reader.
    //Is there a better way around this? Might be an issue for uploading to databases.
    @Override
    public void writeSTAR3Header(Writer chan) throws IOException {
        char stringQuote = '"';
        chan.write("save_" + getName() + "\n");
        chan.write("_Spectral_peak_list.Sf_category                   ");
        chan.write("spectral_managed_peak_list\n");
        chan.write("_Spectral_peak_list.Sf_framecode                  ");
        chan.write(getName() + "\n");
        chan.write("_Spectral_peak_list.ID                            ");
        chan.write(getId() + "\n");
        chan.write("_Spectral_peak_list.Data_file_name                ");
        chan.write(".\n");
        chan.write("_Spectral_peak_list.Sample_ID                     ");
        chan.write(acquisition.getSample().getId()+"\n");
        chan.write("_Spectral_peak_list.Sample_label                  ");
        if (getSampleLabel().length() != 0) {
            chan.write("'" + getSampleLabel() + "'\n");
        } else {
            chan.write(".\n");
        }
        chan.write("_Spectral_peak_list.Sample_condition_list_ID      ");
        chan.write(acquisition.getCondition().getId()+"\n");
        chan.write("_Spectral_peak_list.Sample_condition_list_label   ");
        String sCond = getSampleConditionLabel();
        if ((sCond.length() != 0) && !sCond.equals(".")) {
            chan.write("'" + sCond + "'\n");
        } else {
            chan.write(".\n");
        }
        chan.write("_Spectral_peak_list.Slidable                      ");
        String slidable = isSlideable() ? "yes" : "no";
        chan.write(slidable + "\n");

        chan.write("_Spectral_peak_list.Experiment_ID                 ");
        chan.write(".\n");
        chan.write("_Spectral_peak_list.Experiment_name               ");
        if (fileName.length() != 0) {
            chan.write("$" + fileName + "\n");
        } else {
            chan.write(".\n");
        }
        chan.write("_Spectral_peak_list.Experiment_type               ");
        chan.write("'" + acquisition.getExperiment().toString() + "'\n");
        chan.write("_Spectral_peak_list.Experiment_class              ");
        chan.write("$" + acquisition.getExperiment().toCode() + "\n");
        chan.write("_Spectral_peak_list.Number_of_spectral_dimensions ");
        chan.write(String.valueOf(nDim) + "\n");
        chan.write("_Spectral_peak_list.Details                       ");
        if (getDetails().length() != 0) {
            chan.write(stringQuote + getDetails() + stringQuote + "\n");
        } else {
            chan.write(".\n");
        }
        chan.write("\n");

        chan.write("loop_\n");
        chan.write("_Spectral_dim_transfer.Spectral_dim_ID_1\n");
        chan.write("_Spectral_dim_transfer.Spectral_dim_ID_2\n");
        chan.write("_Spectral_dim_transfer.Indirect\n");
        chan.write("_Spectral_dim_transfer.Spectral_peak_list_ID\n");
        chan.write("_Spectral_dim_transfer.Type\n");
        chan.write("\n");

        ExpDim expDim1=null;
        ExpDim expDim2=null;
        String indirect;
        String type;
        Iterator<ExpDim> obsIterator=acquisition.getExperiment().obsDims.iterator();

        while (obsIterator.hasNext()) {
            if (expDim1==null) {
                expDim1 = obsIterator.next();
            } else {
                expDim1=expDim2;
            }
            expDim2 = obsIterator.next();
            if (expDim2==expDim1.getNextExpDim()) {
                indirect="no";
                type=expDim1.getNextCon().type.toString();
            } else {
                indirect="yes";
                type=".";
            }
            chan.write(String.format("%d %d %s %d %s\n",dimMap.get(expDim1),dimMap.get(expDim2),indirect,getId(),type));
        }
        chan.write("stop_\n");
    }

    public void writePeakConstraintLinks(Writer chan) throws IOException {
        if (noeSet!=null) {
            chan.write("save_peak_constraint_links_" + getId() + "\n");
            chan.write("_Peak_constraint_link_list.Sf_category                   ");
            chan.write("peak_constraint_links\n");
            chan.write("_Peak_constraint_link_list.ID                            ");
            chan.write(getId() + "\n");
            chan.write("_Peak_constraint_link_list.Sf_framecode                  ");
            chan.write("peak_constraint_links_" + getId() + "\n");
            chan.write("_Peak_constraint_link_list.Name                          ");
            chan.write("'"+noeSet.getName() + "'\n");
            chan.write("\n");
            chan.write("loop_\n");
            chan.write("_Peak_constraint_link.Constraint_ID\n");
            chan.write("_Peak_constraint_link.Constraint_ID_item_category\n");
            chan.write("_Peak_constraint_link.Constraint_list_ID\n");
            chan.write("_Peak_constraint_link.Constraint_Sf_category\n");
            chan.write("_Peak_constraint_link.Constraint_Sf_framecode\n");
            chan.write("_Peak_constraint_link.ID\n");
            chan.write("_Peak_constraint_link.Peak_constraint_link_list_ID\n");
            chan.write("_Peak_constraint_link.Peak_ID\n");
            chan.write("_Peak_constraint_link.Spectral_peak_list_ID\n");
            chan.write("_Peak_constraint_link.Spectral_peak_list_Sf_category\n");
            chan.write("_Peak_constraint_link.Spectral_Peak_list_Sf_framecode\n");
            chan.write("\n");

            int ID = 0;
            for (Peak peak : peaks()) {
                ManagedPeak peak2 = (ManagedPeak) peak;
                for (Noe2 noe : peak2.getNoes()) {
                    //todo: investigate NoeSet.getID() replaced with NoeSet.ID
                    chan.write(String.format("%d %s %d %s %s %d %d %d %d spectral_peak_list %s\n", noe.starID, noeSet.getType(), noeSet.ID, noeSet.getCategory(), noeSet.getCategory() + noeSet.getName().replaceAll("\\W", ""), ID++, getId(), peak.getIdNum(), getId(), getName()));
                }
            }
            //a bit hacky to ensure peaklist read in OK.
            if (ID==0) {
                chan.write(String.format("%s %s %d %s %s %d %d %s %d spectral_peak_list %s\n", ".", noeSet.getType(), noeSet.ID, noeSet.getCategory(), noeSet.getCategory() + noeSet.getName().replaceAll("\\W", ""), ID++, getId(), ".", getId(), getName()));
            }
            chan.write("stop_\n");
            chan.write("save_\n\n");
        }
    }
}
