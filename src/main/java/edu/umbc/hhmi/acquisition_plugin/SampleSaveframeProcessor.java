package edu.umbc.hhmi.acquisition_plugin;

import org.nmrfx.star.ParseException;
import org.nmrfx.star.Saveframe;
import org.nmrfx.star.SaveframeProcessor;

import java.io.IOException;

public class SampleSaveframeProcessor implements SaveframeProcessor {

    @Override
    public void process(Saveframe saveframe) throws ParseException, IOException {
        String sampleName = saveframe.getValue("_Sample", "Name").replace("^'", "").replace("'$","");
        System.out.println("process sample");
        Sample sample = Sample.get(sampleName);
        if (sample == null) {
            sample = new Sample(sampleName);
        }
        sample.readSTARSaveFrame(saveframe);
    }
}
