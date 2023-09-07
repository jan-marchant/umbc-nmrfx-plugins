package org.nmrfx.project;

import edu.umbc.hhmi.acquisition_plugin.Condition;
import edu.umbc.hhmi.acquisition_plugin.ManagedNoeSet;
import edu.umbc.hhmi.acquisition_plugin.Sample;
import edu.umbc.hhmi.subproject_plugin.ProjectRelations;
import org.nmrfx.star.SaveframeWriter;
import org.nmrfx.structure.seqassign.RunAbout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProjectUtilities {
    static List<Class> saveFrameRanks = new ArrayList<>(Arrays.asList(ProjectRelations.class, Condition.class, Sample.class, ManagedNoeSet.class, RunAbout.class));

    public static void sortExtraSaveFrames () {
        ProjectBase.getActive().extraSaveframes.sort(ProjectUtilities::compareSaveframes);
    }

    static public int compareSaveframes(SaveframeWriter o1, SaveframeWriter o2) {
        if (o2.getClass().equals(o1.getClass())) {
            if (o1 instanceof Comparable<?>) {
                return ((Comparable) o2).compareTo(o1);
            } else {
                return 0;
            }
        } else {
            return (saveFrameRanks.indexOf(o2.getClass())-saveFrameRanks.indexOf(o1.getClass()));
        }
    }

    public static void removeExtraSaveFrame(ProjectBase parentProject, ProjectRelations projectRelations) {
        parentProject.extraSaveframes.remove(projectRelations);
    }
}

