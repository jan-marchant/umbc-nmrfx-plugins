package edu.umbc.hhmi.subproject_plugin;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.nmrfx.chemistry.Entity;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.utils.GUIUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubProject {
    public ProjectBase project;
    public ObservableList<SubProject> subProjectList = FXCollections.observableArrayList();
    public HashMap<ProjectBase, BidiMap<Entity,Entity>> entityMap = new HashMap<>();
    private static List<SubProject> subProjectsList = new ArrayList<>();

    static public SubProject findSubProject(ProjectBase project) {
        for (SubProject subProj : subProjectsList) {
            if (subProj.getProject() == project) {
                return subProj;
            }
        }
        return null;
    }

    public ProjectBase getProject() {
        return project;
    }

    public static SubProject SetupSubProjects(ProjectBase project) {
        SubProject subProj = findSubProject(project);
        if (subProj==null) {
            subProj = new SubProject(project);
        }
        return subProj;
    }

    private SubProject(ProjectBase proj) {
        project=proj;
        subProjectsList.add(this);
    }

    public boolean containsSubProjectPath(Path path) {
        if (project.getDirectory().equals(path)) {
            return true;
        }
        for (SubProject subProject : subProjectList) {
            if (subProject.containsSubProjectPath(path)) {
                return true;
            }
        }
        return false;
    }

    public void writeSubProjectsStar3(FileWriter chan) throws IOException {
        if (subProjectList.size()<=0) {
            return;
        }
        chan.write("\n\n");
        chan.write("    ####################################\n");
        chan.write("    #      Associated assemblies       #\n");
        chan.write("    ####################################\n");
        chan.write("\n\n");

        int id=0;

        for (SubProject subProj : subProjectList) {
            ProjectBase subProject = subProj.getProject();
            subProject.saveProject();
            Path relativePath;
            try {
                relativePath = project.getDirectory().relativize(subProject.getDirectory());
            } catch (Exception e) {
                relativePath = subProject.getDirectory();
            }
            String label = subProject.getName();
            chan.write("save_" + label + "\n");
            chan.write("_Assembly_subsystem.Sf_category                 ");
            chan.write("assembly_subsystems\n");
            chan.write("_Assembly_subsystem.Sf_framecode                ");
            chan.write("save_" + label + "\n");
            chan.write("_Assembly_subsystem.ID                          ");
            chan.write(id+"\n");
            chan.write("_Assembly_subsystem.Name                        ");
            chan.write("'"+label+"'\n");
            chan.write("_Assembly_subsystem.Details                     ");
            chan.write("'"+relativePath.toString()+"'\n");

            //fixme: this is not an "official" STAR category.
            // using names for fear that IDs aren't persistent
            // (e.g. if subproject edited).
            chan.write("loop_\n");
            chan.write("_Entity_map.Assembly_subsystem_ID\n");
            chan.write("_Entity_map.Active_system\n");
            chan.write("_Entity_map.Sub_system\n");
            chan.write("\n");

            //All this just to write them in residue order...
            List<Entity> seen=new ArrayList<>();
            for (Entity entity : project.getActiveMolecule().getEntities()) {
                if (entity instanceof Polymer) {
                    for (Residue residue : ((Polymer) entity).getResidues()) {
                        if (entityMap.containsKey(project)) {
                            if (entityMap.get(project).containsKey(residue)) {
                                chan.write(String.format("%d %s %s\n", id, residue.toString(), entityMap.get(project).get(residue).toString()));
                                seen.add(residue);
                            }
                        }
                    }
                }
            }
            for (Map.Entry<Entity,Entity> entry : entityMap.get(project).entrySet()) {
                if (!seen.contains(entry.getKey())) {
                    chan.write(String.format("%d %s %s\n", id, entry.getKey().toString(), entry.getValue().toString()));
                }
            }

            chan.write("stop_\n");
            chan.write("save_\n\n");
            id++;
        }
        chan.write("\n\n");
    }

    public void addSubProject(String projectName, String projectPath,
                              List<String> activeEntities, List<String> subEntities) {
        String absolute;
        try {
            File parentDir = new File(project.getDirectory().toString());
            File childDir = new File(parentDir, projectPath);
            absolute = childDir.getCanonicalPath();
        } catch (Exception e) {
            absolute = projectPath;
        }

        Path projPath= Paths.get(absolute);
        ProjectBase subProj=addSubProject(projectName,projPath);
        if (subProj!=null) {
            BidiMap<Entity, Entity> map = new DualHashBidiMap<>();
            entityMap.put(subProj, map);
            for (int i = 0; i < activeEntities.size(); i++) {
                //map.put(activeMol.getEntitiesAndResidues(activeEntities.get(i)), subProj.activeMol.getEntitiesAndResidues(subEntities.get(i)));
            }
        }
    }

    public ProjectBase findSubProject(Path path) {
        if (project.getDirectory().equals(path)) {
            return project;
        }
        for (SubProject subProject : subProjectList) {
            ProjectBase found=subProject.findSubProject(path);
            if (found!=null) {
                return found;
            }
        }
        return null;
    }

    public ProjectBase addSubProject(Path projectPath) {
        if (projectPath==null) {
            return null;
        }
        ProjectBase found=findSubProject(projectPath);
        if (found!=null) {
            if (project.getDirectory().equals(projectPath)) {
                GUIUtils.warn("Error","Cannot add project as a subProject of itself");
                return null;
            } else {
                return found;
            }
        }
        String projectName = projectPath.getFileName().toString();
        return addSubProject(projectName,projectPath);
    }

    public ProjectBase addSubProject(String name, Path path) {
        GUIProject subProj;
        try {
            subProj = new GUIProject(name);
            this.getProject().setActive();
            subProj.loadGUIProject(path);
            SetupSubProjects(subProj);
        } catch (Exception e) {
            subProj=null;
        }
        return subProj;
    }

    public List<Object> getSubProjMenus(SubProjMenu controller) {
        List<Object> menus=new ArrayList<>();
        for (SubProject subProj : subProjectList) {
            if (subProj.subProjectList.size()>0) {
                Menu menu = new Menu(subProj.getProject().getName());
                for (Object subMenu : subProj.getSubProjMenus(controller)) {
                    if (subMenu instanceof Menu) {
                        menu.getItems().add((Menu) subMenu);
                    } else {
                        menu.getItems().add((MenuItem) subMenu);
                    }
                }
                menus.add(menu);
                menu.setOnAction(e -> controller.setSubProject(subProj.getProject()));
            } else {
                MenuItem menu = new MenuItem(subProj.getProject().getName());
                menus.add(menu);
                menu.setOnAction(e -> controller.setSubProject(subProj.getProject()));
            }
        }
        return menus;
    }
}
