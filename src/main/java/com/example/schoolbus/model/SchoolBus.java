package com.example.schoolbus.model;

import java.util.UUID;

public class SchoolBus {

  private String id;
  private double length;
  private double height;
  private double width;
  private double wheelRadius;
  private double wheelThickness;
  private int windowCount = 5;
  private String colorHex = "#FDB813"; // Alapértelmezett sárga szín

  public SchoolBus() {
    this.id = "bus_" + UUID.randomUUID().toString().substring(0, 5);
    setDimensions(400.0, 150.0, 120.0, 30.0, 20.0, 5);
  }

  public void setDimensions(double l, double h, double w, double wr, double wt, int wc) {
    if (w < 80) w = 80;
    if (w > 300) w = 300;

    if (l < w * 2.5) l = w * 2.5;
    if (l > w * 5) l = w * 5;

    if (h < w * 0.8) h = w * 0.8;
    if (h > w * 2.0) h = w * 2.0;

    if (wr < h * 0.1) wr = h * 0.1;
    if (wr > h * 0.35) wr = h * 0.35;

    if (wt < w * 0.1) wt = w * 0.1;
    if (wt > w * 0.3) wt = w * 0.3;

    if (wc < 2) wc = 2;
    if (wc > 10) wc = 10;

    this.length = l;
    this.height = h;
    this.width = w;
    this.wheelRadius = wr;
    this.wheelThickness = wt;
    this.windowCount = wc;
  }

  public double getVolume() {
    double bodyVolume = length * width * height;
    double wheelVolume = 4 * (Math.PI * Math.pow(wheelRadius, 2) * wheelThickness);
    return bodyVolume + wheelVolume;
  }

  public double getSurfaceArea() {
    double bodySurface = 2 * (length * width + length * height + width * height);
    double wheelSurface = 4 * (2 * Math.PI * Math.pow(wheelRadius, 2) + 2 * Math.PI * wheelRadius * wheelThickness);
    return bodySurface + wheelSurface;
  }

  public void randomize() {
    java.util.Random rand = new java.util.Random();
    double w = 80 + rand.nextDouble() * 100;
    double l = w * (2.5 + rand.nextDouble() * 2);
    double h = w * (0.9 + rand.nextDouble() * 0.9);
    double wr = h * (0.15 + rand.nextDouble() * 0.15);
    double wt = w * (0.1 + rand.nextDouble() * 0.15);
    int wc = 3 + rand.nextInt(6);
    setDimensions(l, h, w, wr, wt, wc);

    String[] randomColors = {"#FDB813", "#E74C3C", "#2ECC71", "#3498DB", "#9B59B6", "#FFFFFF"};
    this.colorHex = randomColors[rand.nextInt(randomColors.length)];
  }

  // Getterek / Setterek
  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public double getLength() { return length; }
  public double getHeight() { return height; }
  public double getWidth() { return width; }
  public double getWheelRadius() { return wheelRadius; }
  public double getWheelThickness() { return wheelThickness; }
  public int getWindowCount() { return windowCount; }
  public String getColorHex() { return colorHex; }
  public void setColorHex(String colorHex) { this.colorHex = colorHex; }
}