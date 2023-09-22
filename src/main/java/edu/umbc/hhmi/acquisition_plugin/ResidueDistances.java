package edu.umbc.hhmi.acquisition_plugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

public class ResidueDistances {

    static List<ResidueDistances> distancesList = new ArrayList<>();


    String type;
    String res1;
    String res2;
    HashMap<String[],double []> distances = new HashMap<>();

    ResidueDistances(String type, String res1, String res2) {
        this.type=type;
        this.res1=res1;
        this.res2=res2;
    }

    public void add (String a1,String a2,double distance, double nInst) {
        String[] sArr = new String[2];
        double[] dArr = new double[2];
        sArr[0]=a1;
        sArr[1]=a2;
        dArr[0]=0;
        dArr[1]=0;
        distances.putIfAbsent(sArr,dArr);
        distances.get(sArr)[0]+=distance*nInst;
        distances.get(sArr)[1]+=nInst;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ResidueDistances) {
            //System.out.println("Comparing "+type+res1+res2+" with "+((ResidueDistances) obj).type+((ResidueDistances) obj).res1+((ResidueDistances) obj).res2);
            return type.equals(((ResidueDistances) obj).type) &&
                    res1.equals(((ResidueDistances) obj).res1) &&
                    res2.equals(((ResidueDistances) obj).res2);
        }
        return false;
    }

    static {
        try {
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            InputStream iStream = cl.getResourceAsStream("data/res_pair_table.txt");
            if (iStream != null) {
                Scanner inputStream = new Scanner(iStream);
                int row = 0;
                while (inputStream.hasNextLine()) {
                    String line = inputStream.nextLine();
                    if (!line.isEmpty()) {
                        String[] data = line.split("\t");
                        if (data.length != 9) {
                            System.out.println("Check res_pair_table line " + row);
                        }
                        if (row++ == 0) {
                            continue;
                        }
                        //interType,res1,res2,atom1,atom2,minDis,maxDis,avgDis,nInst
                        if (data[3].charAt(data[3].length() - 1) == '\'') {
                            data[1] = "r";
                        }
                        if (data[4].charAt(data[4].length() - 1) == '\'') {
                            data[2] = "r";
                        }
                        ResidueDistances distance = new ResidueDistances(data[0].trim(), data[1].trim(), data[2].trim());
                        int index = distancesList.indexOf(distance);
                        if (index == -1) {
                            distance.add(data[3].trim(), data[4].trim(), Double.parseDouble(data[7]), Double.parseDouble(data[8]));
                            distancesList.add(distance);
                            //System.out.println("Added new ResidueDistance "+data[0].trim()+data[1].trim()+data[2].trim());
                        } else {
                            distancesList.get(index).add(data[3].trim(), data[4].trim(), Double.parseDouble(data[7]), Double.parseDouble(data[8]));
                            //System.out.println("Added new distances to "+data[0].trim()+data[1].trim()+data[2].trim());
                        }
                    }
                }
                iStream.close();
            }
        } catch (IOException e) {
            System.out.println("Couldn't read res_pair_table");
        }
    }


}
