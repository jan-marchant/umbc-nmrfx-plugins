package org.nmrfx.project;

import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.io.*;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.ResonanceFactory;
import org.nmrfx.processor.gui.PreferencesController;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.processor.project.Project;
import org.nmrfx.star.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SubProject extends GUIProject {
    private static final Logger log = LoggerFactory.getLogger(SubProject.class);

    public static Map<ProjectBase ,ResonanceFactory> resFactoryMap = new HashMap();

    public static ResonanceFactory resFactory() {
        return resFactory(ProjectBase.getActive());
    }

    public static ResonanceFactory resFactory(ProjectBase project) {
        if (!(ProjectBase.getActive() instanceof SubProject)) {
            return PeakList.resFactory();
        }
        /*ResonanceFactory resFactory = resFactoryMap.get(ProjectBase.getActive());
        if (resFactory == null) {
            resFactory = new ResonanceFactory();
            resFactoryMap.put(ProjectBase.getActive(),resFactory);
        }
         */
        return resFactoryMap.computeIfAbsent(ProjectBase.getActive(),k -> new ResonanceFactory());
        //return resFactory;
    }

    //All this just to avoid opening windows...
    //Will we have issues using a single ResonanceFactory for all projects
    //Too difficult for me to overcome if so

    public SubProject(String name) {
        super(name);
    }

    public void loadSubProject(Path projectDir) throws IOException, MoleculeIOException {
        ProjectBase currentProject = getActive();
        setActive();

        loadProject(projectDir, "datasets");
        FileSystem fileSystem = FileSystems.getDefault();

        String[] subDirTypes = {"star", "peaks", "molecules", "shifts", "refshifts", "windows"};
        if (projectDir != null) {
            boolean readSTAR3 = false;
            for (String subDir : subDirTypes) {
                log.debug("read {} {}", subDir, readSTAR3);
                Path subDirectory = fileSystem.getPath(projectDir.toString(), subDir);
                if (Files.exists(subDirectory) && Files.isDirectory(subDirectory) && Files.isReadable(subDirectory)) {
                    switch (subDir) {
                        case "star":
                            readSTAR3 = loadSTAR3(subDirectory);
                            break;
                        case "molecules":
                            if (!readSTAR3) {
                                loadMolecules(subDirectory);
                            }
                            break;
                        case "peaks":
                            if (!readSTAR3) {
                                log.debug("readpeaks");
                                loadProject(projectDir, "peaks");
                            } else {
                                loadProject(projectDir, "mpk2");
                            }
                            break;
                        case "shifts":
                            if (!readSTAR3) {
                                loadShiftFiles(subDirectory, false);
                            }
                            break;
                        case "refshifts":
                            loadShiftFiles(subDirectory, true);
                            break;
                        case "windows":
                            //not for subProject
                            //loadWindows(subDirectory);
                            break;

                        default:
                            throw new IllegalStateException("Invalid subdir type");
                    }
                }

            }
        }
        //Let's not add this to recent projects
        //setProjectDir(projectDir);
        //PreferencesController.saveRecentProjects(projectDir.toString());
        currentProject.setActive();
    }

    private File getSTAR3FileName(Path directory) {
        Path starFile = FileSystems.getDefault().getPath(directory.toString(), projectDir.getFileName().toString() + ".str");
        return starFile.toFile();

    }

    boolean loadSTAR3(Path directory) throws IOException {
        File starFile = getSTAR3FileName(directory);
        boolean result = false;
        if (starFile.exists()) {
            try {
                SubNMRStarReader.readSub(starFile);
                result = true;
            } catch (ParseException ex) {
                throw new IOException(ex.getMessage());
            }
        }
        return result;
    }

    void loadMolecules(Path directory) throws MoleculeIOException {
        Path sstructPath = null;
        if (Files.isDirectory(directory)) {
            try (DirectoryStream<Path> fileStream = Files.newDirectoryStream(directory)) {
                for (Path path : fileStream) {
                    if (Files.isDirectory(path)) {
                        loadMoleculeEntities(path);
                    } else {
                        if (path.toString().endsWith(".2str")) {
                            sstructPath = path;
                        } else {
                            loadMolecule(path);
                        }
                    }

                }
            } catch (DirectoryIteratorException | IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
        if (sstructPath != null) {
            try {
                List<String> content = Files.readAllLines(sstructPath);
                if (MoleculeFactory.getActive() != null) {
                    loadSecondaryStructure(MoleculeFactory.getActive(), content);
                }
            } catch (IOException ioE) {
                throw new MoleculeIOException(ioE.getMessage());
            }
        }
    }

    void loadSecondaryStructure(MoleculeBase molecule, List<String> fileContent) {
        // fixme use yaml ?
        for (String s : fileContent) {
            if (s.contains(("vienna"))) {
                String[] fields = s.split(":");
                if (fields.length == 2) {
                    molecule.setDotBracket(fields[1].trim());
                }
            }
        }

    }

    public static void loadMolecule(Path file) throws MoleculeIOException {
        if (file.toString().endsWith(".pdb")) {
            PDBFile pdbReader = new PDBFile();
            pdbReader.readSequence(file.toString(), false, 0);
        } else if (file.toString().endsWith(".sdf")) {
            SDFile.read(file.toString(), null);
        } else if (file.toString().endsWith(".mol")) {
            SDFile.read(file.toString(), null);
        } else if (file.toString().endsWith(".seq")) {
            Sequence seq = new Sequence();
            seq.read(file.toString());
        }
        if (MoleculeFactory.getActive() == null) {
            throw new MoleculeIOException("Couldn't open any molecules");
        }
        log.info("active mol {}", MoleculeFactory.getActive().getName());
    }

    void loadMoleculeEntities(Path directory) throws MoleculeIOException, IOException {
        String molName = directory.getFileName().toString();
        MoleculeBase mol = MoleculeFactory.newMolecule(molName);
        PDBFile pdbReader = new PDBFile();
        Pattern pattern = Pattern.compile("(.+)\\.(seq|pdb|mol|sdf)");
        Predicate<String> predicate = pattern.asPredicate();
        if (Files.isDirectory(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                files.sequential().filter(path -> predicate.test(path.getFileName().toString())).
                        sorted(new FileComparator()).
                        forEach(path -> {
                            String pathName = path.toString();
                            String fileName = path.getFileName().toString();
                            Matcher matcher = pattern.matcher(fileName);
                            String baseName = matcher.group(1);

                            try {
                                if (fileName.endsWith(".seq")) {
                                    Sequence sequence = new Sequence();
                                    sequence.read(pathName);
                                } else if (fileName.endsWith(".pdb")) {
                                    if (mol.entities.isEmpty()) {
                                        pdbReader.readSequence(pathName, false, 0);
                                    } else {
                                        PDBFile.readResidue(pathName, null, mol, baseName);
                                    }
                                } else if (fileName.endsWith(".sdf")) {
                                    SDFile.read(pathName, null, mol, baseName);
                                } else if (fileName.endsWith(".mol")) {
                                    SDFile.read(pathName, null, mol, baseName);
                                }
                            } catch (MoleculeIOException molE) {
                                log.warn(molE.getMessage(), molE);
                            }

                        });
            }
        }
    }

    void loadShiftFiles(Path directory, boolean refMode) throws MoleculeIOException, IOException {
        MoleculeBase mol = MoleculeFactory.getActive();
        Pattern pattern = Pattern.compile("(.+)\\.(txt|ppm)");
        Predicate<String> predicate = pattern.asPredicate();
        if (Files.isDirectory(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                files.sequential().filter(path -> predicate.test(path.getFileName().toString())).
                        sorted(new FileComparator()).
                        forEach(path -> {
                            String fileName = path.getFileName().toString();
                            Optional<Integer> fileNum = getIndex(fileName);
                            int ppmSet = fileNum.isPresent() ? fileNum.get() : 0;
                            PPMFiles.readPPM(mol, path, ppmSet, refMode);
                        });
            }
        }
    }

    @Override
    public void saveProject() throws IOException {
        //Not currently possible (/easy) due to Resonance handling
        log.warn("Cannot save SubProject yet - sorry!");
    }

    @Override
    public String toString() {
        return getName();
    }
}
