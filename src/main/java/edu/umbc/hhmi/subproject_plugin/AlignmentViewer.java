package edu.umbc.hhmi.subproject_plugin;

import javafx.scene.control.IndexRange;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.apache.commons.collections4.BidiMap;
import org.nmrfx.chemistry.Entity;
import org.nmrfx.chemistry.NeedlemanWunsch;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.SmithWaterman;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AlignmentViewer extends TextArea {

    @Override
    public void appendText(String text) {
    }

    @Override
    public void insertText(int index, String text) {
    }

    @Override
    public void deleteText(IndexRange range) {
    }

    @Override
    public void deleteText(int start, int end) {
    }

    @Override
    public void replaceText(IndexRange range, String text) {
    }

    @Override
    public void replaceText(int start, int end, String text) {
    }

    @Override
    public void cut() {
    }

    @Override
    public void paste() {
    }

    StringBuilder[] sequences_sub_main = new StringBuilder[2];
    Polymer subprojectPolymer;
    Polymer mainProjectPolymer;
    int lineLength=60;
    String pipes = new String(new char[lineLength]).replace("\0", "|");
    String spaces = new String(new char[lineLength]).replace("\0", " ");
    enum MatchType {
        GAP,
        MISMATCH,
        MATCH
    }
    Map<MatchType,String> bgStyle = Map.of(MatchType.GAP,"\\u001B[0m", MatchType.MISMATCH,"\u001B[41m", MatchType.MATCH,"\u001B[42m");

    public AlignmentViewer() {
        addEventFilter(KeyEvent.ANY, event -> {
            KeyCode code = event.getCode();
            if (event.getEventType()==KeyEvent.KEY_PRESSED) {
                if (code==KeyCode.SPACE || code==KeyCode.DELETE || code == KeyCode.BACK_SPACE) {
                    int offset = this.getCaretPosition();
                    int lineno=offset/(lineLength+1);
                    int column=offset%(lineLength+1);
                    int insertPos=(lineno/4)*lineLength+column;
                    if (lineno%2==0) {
                        updateSeq((lineno%4)/2,code,insertPos,offset);
                    }
                }
            }
            if (!(code==KeyCode.UP || code==KeyCode.DOWN || code == KeyCode.LEFT || code == KeyCode.RIGHT ||
                    code==KeyCode.KP_UP || code==KeyCode.KP_DOWN || code == KeyCode.KP_LEFT || code == KeyCode.KP_RIGHT)) {
                event.consume();
            }

        });

        setPrefWidth(650);
        setMinWidth(650);
        setPrefHeight(300);
        setFont(Font.font("Courier New", FontWeight.NORMAL, 16));
    }

    public void alignFromMap(Entity subPolymer, Entity mainPolymer, BidiMap<Entity,Entity> map_main_sub) {
        if (!checkEntities(subPolymer,mainPolymer)) {
            setText("");
            return;
        }
        List<List<Integer>> lists= new ArrayList<>(2);
        List<Integer> subList=new ArrayList<>();
        List<Integer> mainList=new ArrayList<>();

        lists.add(subList);
        lists.add(mainList);
        int nextAlignedID=1;
        for (Residue residue : ((Polymer) subPolymer).getResidues()) {
            Residue aligned = (Residue) map_main_sub.getKey(residue);
            if (aligned == null) {
                subList.add(residue.getIDNum() - 1);
                mainList.add(null);
            } else {
                int alignedID = aligned.getIDNum();
                for (int i = nextAlignedID; i < alignedID; i++) {
                    subList.add(null);
                    mainList.add(i - 1);
                }
                subList.add(residue.getIDNum() - 1);
                mainList.add(aligned.getIDNum() - 1);
                nextAlignedID = alignedID+1;
            }
        }
        for (Residue aligned2 : ((Polymer) mainPolymer).getResidues()) {
            int alignedID = aligned2.getIDNum();
            if (alignedID >= nextAlignedID) {
                subList.add(null);
                mainList.add(alignedID-1);
                nextAlignedID = alignedID;
            }
        }

        layoutStrings(lists);
        generateText();
    }
    public void align(Entity polymer1, Entity polymer2) {
        if (!checkEntities(polymer1,polymer2)) {
            setText("");
            return;
        }
        alignEntities();
        generateText();
    }

    public boolean checkEntities(Entity polymer1, Entity polymer2) {
        if (!(polymer1 instanceof Polymer) || !(polymer2 instanceof Polymer) ||
                !((Polymer) polymer1).getPolymerType().equals(((Polymer) polymer2).getPolymerType())) {
            return false;
        }
        this.subprojectPolymer =(Polymer) polymer1;
        this.mainProjectPolymer =(Polymer) polymer2;
        sequences_sub_main[0]=new StringBuilder();
        sequences_sub_main[1]=new StringBuilder();
        sequences_sub_main[0].append(this.subprojectPolymer.getOneLetterCode());
        sequences_sub_main[1].append(this.mainProjectPolymer.getOneLetterCode());
        return true;
    }

    public void alignEntities() {
        //SmithWaterman aligner = new SmithWaterman(subprojectPolymer.getOneLetterCode(), mainProjectPolymer.getOneLetterCode());
        NeedlemanWunsch aligner = new NeedlemanWunsch(subprojectPolymer.getOneLetterCode(), mainProjectPolymer.getOneLetterCode(),1,-7,-3,-1);
        aligner.buildMatrix();
        //aligner.dumpH();
        aligner.processMatrix();
        List<List<Integer>> lists = new ArrayList<>(2);
        lists.add(aligner.getA());
        lists.add(aligner.getB());
        layoutStrings(lists);
    }
    public void layoutStrings(List<List<Integer>> lists) {
        sequences_sub_main[0].setLength(0);
        sequences_sub_main[1].setLength(0);
        Polymer[] polymers = {subprojectPolymer, mainProjectPolymer};
        for (int listIndex = 0; listIndex < 2; listIndex++) {
            for (int i = 0; i < lists.get(listIndex).size(); i++) {
                if (lists.get(listIndex).get(i) == null) {
                    sequences_sub_main[listIndex].append(" ");
                } else {
                    sequences_sub_main[listIndex].append(polymers[listIndex].getResidue(lists.get(listIndex).get(i)).getOneLetter());
                }
            }
        }
    }


    private void updateSeq (int index, KeyCode code, int insertPos,int offset) {
        if(insertPos < sequences_sub_main[index].length()) {
            switch (code) {
                case SPACE:
                    sequences_sub_main[index].insert(insertPos, ' ');
                    generateText();
                    if (offset % (lineLength + 1) == lineLength) {
                        offset += (lineLength * 3 + 4);
                    }
                    positionCaret(offset + 1);
                    break;
                case DELETE:
                    if (sequences_sub_main[index].charAt(insertPos) == ' ') {
                        sequences_sub_main[index].deleteCharAt(insertPos);
                        generateText();
                        positionCaret(offset);
                    }
                    //update caret
                    break;
                case BACK_SPACE:
                    if (sequences_sub_main[index].charAt(insertPos - 1) == ' ') {
                        sequences_sub_main[index].deleteCharAt(insertPos - 1);
                        generateText();
                        if (offset % (lineLength + 1) == 0 && offset != 0) {
                            offset -= (lineLength * 3 + 4);
                        }
                        positionCaret(offset - 1);
                    }
                    //update caret
                    break;
            }
        }
    }

    public void generateText() {
        StringBuilder s = new StringBuilder();

        int numLines = 1 + Math.max(sequences_sub_main[0].length(), sequences_sub_main[1].length()) / lineLength;
        int numChars = numLines * lineLength;
        while (sequences_sub_main[0].length() < numChars) {
            sequences_sub_main[0].append(' ');
        }
        while (sequences_sub_main[1].length() < numChars) {
            sequences_sub_main[1].append(' ');
        }
        MatchType currentMatch = MatchType.GAP;
        for (int i = 0; i < numLines; i++) {
            for (int j = 0; j < lineLength; j++) {
                MatchType newMatch = getMatchType(sequences_sub_main[0].charAt(lineLength * i+j), sequences_sub_main[1].charAt(lineLength * i+j));
                if (newMatch != currentMatch) {
                    currentMatch = newMatch;
                    //s.append(bgStyle.get(newMatch));
                }
                s.append(sequences_sub_main[0],lineLength * i+j,lineLength * i+j+1);
            }
            currentMatch = MatchType.GAP;
            //s.append(bgStyle.get(MatchType.GAP));
            s.append("\n");
            //s.append(pipes);
            for (int j = 0; j < lineLength; j++) {
                MatchType match = getMatchType(sequences_sub_main[0].charAt(lineLength * i+j), sequences_sub_main[1].charAt(lineLength * i+j));
                switch (match) {
                    case MatchType.MATCH:
                        s.append("|");
                        break;
                    case MatchType.MISMATCH:
                        s.append("X");
                        break;
                    default:
                        s.append(" ");
                }
            }
            s.append("\n");
            for (int j = 0; j < lineLength; j++) {
                MatchType newMatch = getMatchType(sequences_sub_main[0].charAt(lineLength * i+j), sequences_sub_main[1].charAt(lineLength * i+j));
                if (newMatch != currentMatch) {
                    currentMatch = newMatch;
                    //s.append(bgStyle.get(newMatch));
                }
                s.append(sequences_sub_main[1],lineLength * i+j,lineLength * i+j+1);
            }
            currentMatch = MatchType.GAP;
            //s.append(bgStyle.get(MatchType.GAP));
            s.append("\n");
            s.append(spaces);
            s.append("\n");
        }
        sequences_sub_main[0] = new StringBuilder(sequences_sub_main[0].toString().replaceAll("\\s+$", ""));
        sequences_sub_main[1] = new StringBuilder(sequences_sub_main[1].toString().replaceAll("\\s+$", ""));
        setText(s.toString());
    }

    private MatchType getMatchType(char c, char c1) {
        if (c1 == ' ' || c == ' ') {
            return MatchType.GAP;
        }
        if (c == c1) {
            return MatchType.MATCH;
        }
        return MatchType.MISMATCH;
    }
}
