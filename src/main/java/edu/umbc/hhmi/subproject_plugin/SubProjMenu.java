package edu.umbc.hhmi.subproject_plugin;

import org.nmrfx.project.ProjectBase;

public interface SubProjMenu {
    void setSubProject(ProjectBase subProject);

    public static boolean isSubProjectPresent() {
        try {
            Class.forName("edu.umbc.hhmi.subproject_plugin.SubProject");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

}
