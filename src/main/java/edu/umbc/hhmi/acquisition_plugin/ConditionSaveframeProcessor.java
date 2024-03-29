package edu.umbc.hhmi.acquisition_plugin;

import org.nmrfx.star.ParseException;
import org.nmrfx.star.Saveframe;
import org.nmrfx.star.SaveframeProcessor;

public class ConditionSaveframeProcessor implements SaveframeProcessor {
    @Override
    public void process(Saveframe saveframe) throws ParseException {
        //String name = saveframe.getName();
        //is this the same?
        String name = saveframe.getValue("_Sample_condition_list", "Name").replace("^'", "").replace("'$","");
        System.out.println("process condition");
        Condition condition = Condition.get(name);
        if (condition == null) {
            condition = new Condition(name);
        }
        condition.readSTARSaveFrame(saveframe);
    }
}
