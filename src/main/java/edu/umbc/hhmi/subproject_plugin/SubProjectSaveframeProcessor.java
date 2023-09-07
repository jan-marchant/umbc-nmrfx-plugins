package edu.umbc.hhmi.subproject_plugin;

import org.nmrfx.star.Loop;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.Saveframe;
import org.nmrfx.star.SaveframeProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SubProjectSaveframeProcessor implements SaveframeProcessor {
    @Override
    public void process(Saveframe saveframe) throws ParseException, IOException {
        System.out.println("process subproject");
        String projectName = saveframe.getValue("_Assembly_subsystem", "Name").replace("^'", "").replace("'$","");
        String projectPath = saveframe.getValue("_Assembly_subsystem", "Details").replace("^'", "").replace("'$","");
        Loop loop = saveframe.getLoop("_Entity_map");
        List<String> activeEntities=new ArrayList<>();
        List<String> subEntities=new ArrayList<>();
        if (loop != null) {
            activeEntities = loop.getColumnAsList("Active_system");
            subEntities = loop.getColumnAsList("Sub_system");
        }
        ProjectRelations.addSubProject(projectName,projectPath,activeEntities,subEntities);
    }
}
