package edu.umbc.hhmi.acquisition_plugin;

import org.nmrfx.star.ParseException;
import org.nmrfx.star.Saveframe;
import org.nmrfx.star.SaveframeProcessor;

import java.io.IOException;

public class ManagedNoeSetSaveframeProcessor implements SaveframeProcessor {
    @Override
    public void process(Saveframe saveframe) throws ParseException, IOException {
        // At the moment this process both "general_distance_constraints2" and "peak_constraint_links"
        System.out.println(String.format("process %s",saveframe.getCategoryName()));
    }
}
