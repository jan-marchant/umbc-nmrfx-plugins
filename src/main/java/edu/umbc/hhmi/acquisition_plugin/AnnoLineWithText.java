package edu.umbc.hhmi.acquisition_plugin;

import javafx.scene.paint.Paint;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.processor.gui.annotations.AnnoLine;

public class AnnoLineWithText extends AnnoLine {

    String text;
    double textX;
    double textY;

    public AnnoLineWithText(String text, double textX, double textY, double x1, double y1, double x2, double y2,
                                POSTYPE xPosType, POSTYPE yPosType) {
        super(x1,y1,x2,y2);
        this.setXPosType(xPosType);
        this.setYPosType(yPosType);
        this.text=text;
        this.textX=textX;
        this.textY=textY;
    }

    @Override
    public boolean hit(double x, double y, boolean selectMode) {
        //todo: make configurable
        return false;
    }

    @Override
    public void draw(GraphicsContextInterface gC, double[][] bounds, double[][] world) {
        super.draw(gC,bounds,world);
        double xp1 = getXPosType().transform(textX, bounds[0], world[0]);
        double yp1 = getYPosType().transform(textY, bounds[1], world[1]);
        gC.setStroke(Paint.valueOf(getStroke()));
        gC.setLineWidth(getLineWidth());
        gC.fillText(text, xp1, yp1);
    }

    public void setX(double x) {
        setX1(x);
        setX2(x);
        setTextX(x);
    }

    public void setY(double y) {
        setY1(y);
        setY2(y);
        setTextY(y);
    }

    public void setTextX(double textX) {
        this.textX = textX;
    }

    public void setTextY(double textY) {
        this.textY = textY;
    }
}
