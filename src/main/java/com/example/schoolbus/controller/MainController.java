package com.example.schoolbus.controller;

import com.example.schoolbus.io.StorageManager;
import com.example.schoolbus.model.SchoolBus;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.Sphere;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.Preferences;

public class MainController {

  @FXML private Slider lengthSlider, heightSlider, widthSlider;
  @FXML private Slider wheelRadiusSlider, wheelThicknessSlider, windowCountSlider;
  @FXML private ColorPicker colorPicker;
  @FXML private Label idLabel, volumeLabel, surfaceLabel;
  @FXML private Pane canvasPane;
  @FXML private Canvas canvas;
  @FXML private StackPane pane3D;
  @FXML private CheckBox snapToGridCheck, autoRotateCheck, driveModeCheck, nightModeCheck, wireframeCheck, stopSignCheck, doorOpenCheck;
  @FXML private Menu recentFilesMenu;

  private SchoolBus currentBus;
  private boolean isUpdatingUI = false;

  // Fast Load memóriakezelés
  private Preferences prefs = Preferences.userNodeForPackage(MainController.class);
  private static final String RECENT_FILES_KEY = "recent_files_bus";
  private List<String> recentFilePaths = new ArrayList<>();

  // 2D interakciós és animációs változók
  private final double sideX = 80, sideY = 80;
  private final double topX = 80, topY = 450;
  private enum DragMode { NONE, LENGTH, HEIGHT, WIDTH, WHEEL_RADIUS, WHEEL_THICKNESS }
  private DragMode activeDragMode = DragMode.NONE;
  private double current2DWheelAngle = 0;
  private List<double[]> smokeParticles = new ArrayList<>();

  // 3D Biztonságos Csoportok
  private SubScene subScene;
  private PerspectiveCamera camera;
  private Group world3DGroup = new Group();
  private Group busModelGroup = new Group();
  private Group chassisGroup = new Group();
  private Group wheelsGroup = new Group();
  private Group gridFloor = new Group();

  private AmbientLight ambientLight;
  private PointLight sunLight;
  private double mouseOldX, mouseOldY;
  private Rotate rotateX = new Rotate(15, Rotate.X_AXIS);
  private Rotate rotateY = new Rotate(-45, Rotate.Y_AXIS);
  private AnimationTimer globalTimer;
  private double gridScroll = 0;

  private List<Rotate> wheelRotations = new ArrayList<>();
  private List<Node> flashingLights = new ArrayList<>();

  @FXML
  public void initialize() {
    currentBus = new SchoolBus();

    loadRecentFilesFromPrefs();
    updateRecentFilesMenu();

    setupSlidersAndInputs();
    setup3DScene();
    setupCanvasInteractivity();

    canvas.widthProperty().bind(canvasPane.widthProperty());
    canvas.heightProperty().bind(canvasPane.heightProperty());
    canvas.widthProperty().addListener(obs -> draw2DBus());
    canvas.heightProperty().addListener(obs -> draw2DBus());

    globalTimer = new AnimationTimer() {
      @Override
      public void handle(long now) {
        boolean needs2DRedraw = false;

        if (autoRotateCheck.isSelected()) {
          rotateY.setAngle(rotateY.getAngle() + 0.5);
        }

        if (driveModeCheck.isSelected()) {
          for (Rotate r : wheelRotations) r.setAngle(r.getAngle() - 5);
          gridScroll += 5;
          if (gridScroll >= 100) gridScroll -= 100;
          gridFloor.setTranslateX(gridScroll);

          double bounce = Math.sin(now / 50_000_000.0) * (currentBus.getHeight() * 0.015);
          chassisGroup.setTranslateY(bounce);

          current2DWheelAngle -= 5;

          if (Math.random() > 0.6) {
            double l = currentBus.getLength(), h = currentBus.getHeight();
            smokeParticles.add(new double[]{sideX + l, sideY + h - 15, 0, 5 + Math.random()*5});
          }
          needs2DRedraw = true;
        } else {
          chassisGroup.setTranslateY(0);
        }

        if (!smokeParticles.isEmpty()) {
          Iterator<double[]> it = smokeParticles.iterator();
          while(it.hasNext()) {
            double[] p = it.next();
            p[0] += 2 + Math.random()*2;
            p[1] -= 0.5 + Math.random();
            p[2] += 0.02;
            p[3] += 0.5;
            if (p[2] > 1.0) it.remove();
          }
          needs2DRedraw = true;
        }

        if ((stopSignCheck.isSelected() || doorOpenCheck.isSelected()) && !flashingLights.isEmpty()) {
          boolean flash = (now / 400_000_000) % 2 == 0;
          for (Node light : flashingLights) {
            if (light instanceof Cylinder) ((Cylinder) light).setMaterial(flash ? new PhongMaterial(Color.RED) : new PhongMaterial(Color.DARKRED));
            else if (light instanceof Box) ((Box) light).setMaterial(flash ? new PhongMaterial(Color.RED) : new PhongMaterial(Color.DARKRED));
          }
        } else {
          for (Node light : flashingLights) {
            if (light instanceof Cylinder) ((Cylinder) light).setMaterial(new PhongMaterial(Color.DARKRED));
            else if (light instanceof Box) ((Box) light).setMaterial(new PhongMaterial(Color.DARKRED));
          }
        }

        if (needs2DRedraw && canvas.getScene() != null) draw2DBus();
      }
    };
    globalTimer.start();

    nightModeCheck.selectedProperty().addListener((o, oldV, newV) -> updateUI());
    wireframeCheck.selectedProperty().addListener((o, oldV, newV) -> updateUI());
    stopSignCheck.selectedProperty().addListener((o, oldV, newV) -> updateUI());
    doorOpenCheck.selectedProperty().addListener((o, oldV, newV) -> updateUI());

    updateUI();
  }

  private void setup3DScene() {
    camera = new PerspectiveCamera(true);
    handleResetCamera();
    camera.setNearClip(0.1); camera.setFarClip(20000.0);

    busModelGroup.getChildren().addAll(chassisGroup, wheelsGroup);
    world3DGroup.getTransforms().addAll(rotateX, rotateY);
    world3DGroup.getChildren().add(busModelGroup);

    PhongMaterial gridMat = new PhongMaterial(Color.web("#555555"));
    for (int i = -3000; i <= 3000; i += 100) {
      Box lineX = new Box(6000, 1, 2); lineX.setTranslateZ(i); lineX.setMaterial(gridMat);
      Box lineZ = new Box(2, 1, 6000); lineZ.setTranslateX(i); lineZ.setMaterial(gridMat);
      gridFloor.getChildren().addAll(lineX, lineZ);
    }
    Box solidFloor = new Box(6000, 2, 600);
    solidFloor.setTranslateY(2);
    solidFloor.setMaterial(new PhongMaterial(Color.web("#111111")));
    gridFloor.getChildren().add(solidFloor);
    gridFloor.setTranslateY(150);
    world3DGroup.getChildren().add(gridFloor);

    Group root3D = new Group(world3DGroup);

    ambientLight = new AmbientLight(Color.rgb(150, 150, 150));
    sunLight = new PointLight(Color.WHITE);
    sunLight.setTranslateX(-800); sunLight.setTranslateY(-1000); sunLight.setTranslateZ(-1500);
    root3D.getChildren().addAll(ambientLight, sunLight);

    subScene = new SubScene(root3D, 800, 600, true, SceneAntialiasing.BALANCED);
    subScene.setCamera(camera);
    subScene.setFill(Color.web("#1a1a1a"));

    subScene.widthProperty().bind(pane3D.widthProperty());
    subScene.heightProperty().bind(pane3D.heightProperty());
    pane3D.getChildren().add(subScene);

    subScene.setOnMousePressed(me -> { mouseOldX = me.getSceneX(); mouseOldY = me.getSceneY(); });
    subScene.setOnMouseDragged(me -> {
      if (me.isSecondaryButtonDown() || me.isMiddleButtonDown()) {
        double dx = me.getSceneX() - mouseOldX;
        double dy = me.getSceneY() - mouseOldY;
        camera.setTranslateX(camera.getTranslateX() - dx * 2);
        camera.setTranslateY(camera.getTranslateY() - dy * 2);
      } else if (me.isPrimaryButtonDown()) {
        if (autoRotateCheck.isSelected()) return;
        double dx = me.getSceneX() - mouseOldX;
        double dy = me.getSceneY() - mouseOldY;
        rotateX.setAngle(rotateX.getAngle() - dy * 0.5);
        rotateY.setAngle(rotateY.getAngle() + dx * 0.5);
      }
      mouseOldX = me.getSceneX(); mouseOldY = me.getSceneY();
    });

    subScene.setOnScroll(event -> {
      double zoomFactor = 1.1;
      if (event.getDeltaY() > 0) camera.setTranslateZ(Math.min(camera.getTranslateZ() / zoomFactor, -200));
      else camera.setTranslateZ(Math.max(camera.getTranslateZ() * zoomFactor, -5000));
    });
  }

  @FXML public void handleResetCamera() {
    if(camera != null) {
      camera.setTranslateX(0);
      camera.setTranslateY(-100);
      camera.setTranslateZ(-1500);
      rotateX.setAngle(15);
      rotateY.setAngle(-45);
    }
  }

  private void build3DModel() {
    chassisGroup.getChildren().clear();
    wheelsGroup.getChildren().clear();
    wheelRotations.clear();
    flashingLights.clear();

    DrawMode dMode = wireframeCheck.isSelected() ? DrawMode.LINE : DrawMode.FILL;
    PhongMaterial bodyMat = new PhongMaterial(Color.web(currentBus.getColorHex()));

    PhongMaterial windowMat = new PhongMaterial(Color.web("#87CEEB", 0.4));
    windowMat.setSpecularColor(Color.WHITE);

    boolean isNight = nightModeCheck.isSelected();
    PhongMaterial bulbMat = isNight ? new PhongMaterial(Color.web("#FFFFCC")) : new PhongMaterial(Color.web("#DDDDDD"));
    PhongMaterial redLightMat = new PhongMaterial(Color.RED);
    PhongMaterial seatMat = new PhongMaterial(Color.web("#5C3A21"));

    double l = currentBus.getLength(), h = currentBus.getHeight(), w = currentBus.getWidth();
    double wr = currentBus.getWheelRadius(), wt = currentBus.getWheelThickness();
    int wc = currentBus.getWindowCount();
    double originY = 150 - wr - (h / 2);

    List<Node> opaqueParts = new ArrayList<>();
    List<Node> transparentParts = new ArrayList<>();

    // ZERO-GAP MATEMATIKA
    double lBody = l * 0.8;
    double frontX = -l * 0.3;
    double pW = Math.max(2.0, lBody * 0.04);
    double bayW = (lBody - pW) / wc;
    double winW = bayW - pW;
    double doorW = winW;

    Box floor = new Box(lBody + 0.5, 2, w + 0.5); floor.setMaterial(new PhongMaterial(Color.web("#222222"))); floor.setDrawMode(dMode);
    floor.setTranslateX(frontX + lBody/2.0); floor.setTranslateY(originY + h/2 - 1);

    Box roof = new Box(lBody + 0.5, 2, w + 0.5); roof.setMaterial(bodyMat); roof.setDrawMode(dMode);
    roof.setTranslateX(frontX + lBody/2.0); roof.setTranslateY(originY - h/2 + 1);

    Box frontWallLower = new Box(pW, h*0.55, w); frontWallLower.setMaterial(bodyMat); frontWallLower.setDrawMode(dMode);
    frontWallLower.setTranslateX(frontX + pW/2.0); frontWallLower.setTranslateY(originY + h*0.225);

    Box hood = new Box(l * 0.2 + 0.5, h * 0.45, w * 0.9); hood.setMaterial(bodyMat); hood.setDrawMode(dMode);
    hood.setTranslateX(frontX - l * 0.1); hood.setTranslateY(originY + h * 0.275);

    Box backWall = new Box(pW, h, w); backWall.setMaterial(bodyMat); backWall.setDrawMode(dMode);
    backWall.setTranslateX(frontX + lBody - pW/2.0); backWall.setTranslateY(originY);

    opaqueParts.add(floor); opaqueParts.add(roof); opaqueParts.add(backWall);
    opaqueParts.add(frontWallLower); opaqueParts.add(hood);

    // BELSŐ TÉR ÉS KORMÁNY
    Box driverSeat = new Box(w * 0.25, h * 0.25, w * 0.25); driverSeat.setMaterial(new PhongMaterial(Color.web("#111111"))); driverSeat.setDrawMode(dMode);
    driverSeat.setTranslateX(frontX + pW + winW/2.0); driverSeat.setTranslateY(originY + h * 0.35); driverSeat.setTranslateZ(w * 0.25);

    Cylinder steeringCol = new Cylinder(1.5, h * 0.2); steeringCol.setMaterial(new PhongMaterial(Color.DARKGRAY)); steeringCol.setDrawMode(dMode);
    steeringCol.getTransforms().addAll(new Rotate(60, Rotate.Z_AXIS));
    steeringCol.setTranslateX(frontX + pW + 10); steeringCol.setTranslateY(originY + h * 0.2); steeringCol.setTranslateZ(w * 0.25);

    Cylinder steeringWheel = new Cylinder(h * 0.08, 1.5); steeringWheel.setMaterial(new PhongMaterial(Color.BLACK)); steeringWheel.setDrawMode(dMode);
    steeringWheel.getTransforms().addAll(new Rotate(90, Rotate.X_AXIS), new Rotate(-20, Rotate.Y_AXIS));
    steeringWheel.setTranslateX(frontX + pW + 5); steeringWheel.setTranslateY(originY + h * 0.1); steeringWheel.setTranslateZ(w * 0.25);

    opaqueParts.add(driverSeat); opaqueParts.add(steeringCol); opaqueParts.add(steeringWheel);

    // PILLÉREK ÉS FALAK
    Box leftWallLower = new Box(lBody, h*0.55, 2); leftWallLower.setMaterial(bodyMat); leftWallLower.setDrawMode(dMode);
    leftWallLower.setTranslateX(frontX + lBody/2.0); leftWallLower.setTranslateY(originY + h*0.225); leftWallLower.setTranslateZ(w/2 - 1);
    opaqueParts.add(leftWallLower);

    Box rightWallLower1 = new Box(pW, h*0.55, 2); rightWallLower1.setMaterial(bodyMat); rightWallLower1.setDrawMode(dMode);
    rightWallLower1.setTranslateX(frontX + pW/2.0); rightWallLower1.setTranslateY(originY + h*0.225); rightWallLower1.setTranslateZ(-w/2 + 1);
    opaqueParts.add(rightWallLower1);

    double rw2Len = lBody - pW - doorW;
    Box rightWallLower2 = new Box(rw2Len, h*0.55, 2); rightWallLower2.setMaterial(bodyMat); rightWallLower2.setDrawMode(dMode);
    rightWallLower2.setTranslateX(frontX + pW + doorW + rw2Len/2.0); rightWallLower2.setTranslateY(originY + h*0.225); rightWallLower2.setTranslateZ(-w/2 + 1);
    opaqueParts.add(rightWallLower2);

    Box p0L = new Box(pW, h*0.45, 2); p0L.setMaterial(bodyMat); p0L.setDrawMode(dMode);
    p0L.setTranslateX(frontX + pW/2.0); p0L.setTranslateY(originY - h*0.275); p0L.setTranslateZ(w/2 - 1);
    opaqueParts.add(p0L);

    Box p0R = new Box(pW, h*0.45, 2); p0R.setMaterial(bodyMat); p0R.setDrawMode(dMode);
    p0R.setTranslateX(frontX + pW/2.0); p0R.setTranslateY(originY - h*0.275); p0R.setTranslateZ(-w/2 + 1);
    opaqueParts.add(p0R);

    for (int i = 0; i < wc; i++) {
      double bayCX = frontX + pW + i*bayW + winW/2.0;

      Box winL = new Box(winW + 0.5, h * 0.45, 1); winL.setMaterial(windowMat); winL.setDrawMode(dMode);
      winL.setTranslateX(bayCX); winL.setTranslateY(originY - h*0.275); winL.setTranslateZ(w/2 - 1);
      transparentParts.add(winL);

      if (i == 0) {
        // AJTÓ FELETTI KERET
        double headerH = h * 0.1;
        Box doorHeader = new Box(doorW + 0.5, headerH + 0.5, 2);
        doorHeader.setMaterial(bodyMat); doorHeader.setDrawMode(dMode);
        doorHeader.setTranslateX(bayCX);
        doorHeader.setTranslateY(originY - h/2.0 + headerH/2.0);
        doorHeader.setTranslateZ(-w/2 + 1);
        opaqueParts.add(doorHeader);

        double doorH = h - headerH;
        Group doorGroup = new Group();
        Box doorGlass = new Box(doorW - 0.5, doorH, 1); doorGlass.setMaterial(windowMat); doorGlass.setDrawMode(dMode);
        doorGlass.setTranslateX(doorW / 2.0);
        Box doorFrame = new Box(doorW - 0.5, doorH, 2); doorFrame.setMaterial(new PhongMaterial(Color.DARKGRAY)); doorFrame.setDrawMode(DrawMode.LINE);
        doorFrame.setTranslateX(doorW / 2.0);
        doorGroup.getChildren().addAll(doorGlass, doorFrame);

        doorGroup.setTranslateX(frontX + pW);
        doorGroup.setTranslateY(originY + headerH/2.0);
        doorGroup.setTranslateZ(-w / 2 + 1);

        if (doorOpenCheck.isSelected()) {
          doorGroup.setRotationAxis(Rotate.Y_AXIS); doorGroup.setRotate(85);
        }
        transparentParts.add(doorGroup);
      } else {
        Box winR = new Box(winW + 0.5, h * 0.45, 1); winR.setMaterial(windowMat); winR.setDrawMode(dMode);
        winR.setTranslateX(bayCX); winR.setTranslateY(originY - h*0.275); winR.setTranslateZ(-w/2 + 1);
        transparentParts.add(winR);

        Box seatL = new Box(winW * 0.8, h * 0.25, w * 0.35); seatL.setMaterial(seatMat); seatL.setDrawMode(dMode);
        seatL.setTranslateX(bayCX); seatL.setTranslateY(originY + h * 0.35); seatL.setTranslateZ(w * 0.25);
        Box seatR = new Box(winW * 0.8, h * 0.25, w * 0.35); seatR.setMaterial(seatMat); seatR.setDrawMode(dMode);
        seatR.setTranslateX(bayCX); seatR.setTranslateY(originY + h * 0.35); seatR.setTranslateZ(-w * 0.25);
        opaqueParts.add(seatL); opaqueParts.add(seatR);
      }

      if (i < wc - 1) {
        double pilCX = frontX + pW + i*bayW + winW + pW/2.0;
        Box pLeft = new Box(pW + 0.5, h*0.45 + 0.5, 2); pLeft.setMaterial(bodyMat); pLeft.setDrawMode(dMode);
        pLeft.setTranslateX(pilCX); pLeft.setTranslateY(originY - h*0.275); pLeft.setTranslateZ(w/2 - 1);
        opaqueParts.add(pLeft);

        Box pRight = new Box(pW + 0.5, h*0.45 + 0.5, 2); pRight.setMaterial(bodyMat); pRight.setDrawMode(dMode);
        pRight.setTranslateX(pilCX); pRight.setTranslateY(originY - h*0.275); pRight.setTranslateZ(-w/2 + 1);
        opaqueParts.add(pRight);
      }
    }

    // STOP TÁBLA
    double stopR = h * 0.12;
    Group stopBoard = new Group();
    Cylinder signBoard = new Cylinder(stopR, 2, 8);
    signBoard.setMaterial(redLightMat); signBoard.setDrawMode(dMode);
    signBoard.setRotationAxis(Rotate.X_AXIS); signBoard.setRotate(90);

    Text stFront = new Text("STOP"); stFront.setFill(Color.WHITE); stFront.setFont(Font.font("Arial", FontWeight.BOLD, stopR * 0.55));
    stFront.setTranslateX(-stopR * 0.65); stFront.setTranslateY(stopR * 0.2); stFront.setTranslateZ(-1.2);

    Text stBack = new Text("STOP"); stBack.setFill(Color.WHITE); stBack.setFont(Font.font("Arial", FontWeight.BOLD, stopR * 0.55));
    stBack.setTranslateX(-stopR * 0.65); stBack.setTranslateY(stopR * 0.2);
    Group backTextG = new Group(stBack); backTextG.setRotationAxis(Rotate.Y_AXIS); backTextG.setRotate(180); backTextG.setTranslateZ(1.2);

    Cylinder flashTop = new Cylinder(stopR * 0.15, 3); flashTop.setRotationAxis(Rotate.X_AXIS); flashTop.setRotate(90);
    flashTop.setTranslateY(-stopR * 0.65); flashTop.setTranslateZ(0);
    Cylinder flashBottom = new Cylinder(stopR * 0.15, 3); flashBottom.setRotationAxis(Rotate.X_AXIS); flashBottom.setRotate(90);
    flashBottom.setTranslateY(stopR * 0.65); flashBottom.setTranslateZ(0);
    flashingLights.add(flashTop); flashingLights.add(flashBottom);

    stopBoard.getChildren().addAll(signBoard, stFront, backTextG, flashTop, flashBottom);
    stopBoard.setTranslateX(-stopR);

    Group stopHinge = new Group(stopBoard);
    stopHinge.setTranslateX(frontX + pW + bayW + pW/2.0);
    stopHinge.setTranslateY(originY + h * 0.2);

    if (stopSignCheck.isSelected()) {
      stopHinge.setRotationAxis(Rotate.Y_AXIS); stopHinge.setRotate(-90);
      stopHinge.setTranslateZ(w / 2 + 1 + stopR);
    } else {
      stopHinge.setRotationAxis(Rotate.Y_AXIS); stopHinge.setRotate(0);
      stopHinge.setTranslateZ(w / 2 + 1);
    }
    opaqueParts.add(stopHinge);

    // LÁMPÁK ÉS TETŐVILLOGÓK
    double hoodFrontX = frontX - l * 0.2;
    Box hl1 = new Box(4, h*0.15, w*0.15); hl1.setMaterial(bulbMat); hl1.setTranslateX(hoodFrontX - 2); hl1.setTranslateY(originY+h*0.3); hl1.setTranslateZ(-w*0.3);
    Box hl2 = new Box(4, h*0.15, w*0.15); hl2.setMaterial(bulbMat); hl2.setTranslateX(hoodFrontX - 2); hl2.setTranslateY(originY+h*0.3); hl2.setTranslateZ(w*0.3);

    double rearX = frontX + lBody;
    Box tl1 = new Box(2, h*0.1, w*0.15); tl1.setMaterial(redLightMat); tl1.setTranslateX(rearX + 1); tl1.setTranslateY(originY+h*0.2); tl1.setTranslateZ(-w*0.3);
    Box tl2 = new Box(2, h*0.1, w*0.15); tl2.setMaterial(redLightMat); tl2.setTranslateX(rearX + 1); tl2.setTranslateY(originY+h*0.2); tl2.setTranslateZ(w*0.3);
    opaqueParts.add(hl1); opaqueParts.add(hl2); opaqueParts.add(tl1); opaqueParts.add(tl2);

    if (isNight) {
      Sphere glow1 = new Sphere(w*0.08); glow1.setMaterial(new PhongMaterial(Color.web("#FFFFCC", 0.6)));
      glow1.setTranslateX(hoodFrontX - 2); glow1.setTranslateY(originY+h*0.3); glow1.setTranslateZ(-w*0.3);

      Sphere glow2 = new Sphere(w*0.08); glow2.setMaterial(new PhongMaterial(Color.web("#FFFFCC", 0.6)));
      glow2.setTranslateX(hoodFrontX - 2); glow2.setTranslateY(originY+h*0.3); glow2.setTranslateZ(w*0.3);

      transparentParts.add(glow1); transparentParts.add(glow2);
    }

    double roofY = originY - h/2 + 2;
    for (int i : new int[]{-1, 1}) {
      Box flOut = new Box(3, 6, 6); flOut.setMaterial(redLightMat); flOut.setTranslateX(frontX + 2); flOut.setTranslateY(roofY); flOut.setTranslateZ(i * (w/2 - 5));
      Box flIn = new Box(3, 6, 6); flIn.setMaterial(new PhongMaterial(Color.ORANGE)); flIn.setTranslateX(frontX + 2); flIn.setTranslateY(roofY); flIn.setTranslateZ(i * (w/2 - 15));
      Box blOut = new Box(3, 6, 6); blOut.setMaterial(redLightMat); blOut.setTranslateX(rearX - 2); blOut.setTranslateY(roofY); blOut.setTranslateZ(i * (w/2 - 5));
      Box blIn = new Box(3, 6, 6); blIn.setMaterial(new PhongMaterial(Color.ORANGE)); blIn.setTranslateX(rearX - 2); blIn.setTranslateY(roofY); blIn.setTranslateZ(i * (w/2 - 15));
      opaqueParts.add(flOut); opaqueParts.add(flIn); opaqueParts.add(blOut); opaqueParts.add(blIn);
      flashingLights.add(flOut); flashingLights.add(blOut);
    }

    // SZÉLVÉDŐ
    Box windshield = new Box(1, h * 0.45, w - 2); windshield.setMaterial(windowMat); windshield.setDrawMode(dMode);
    windshield.setTranslateX(frontX + 1); windshield.setTranslateY(originY - h * 0.275);
    transparentParts.add(windshield);

    chassisGroup.getChildren().addAll(opaqueParts);
    chassisGroup.getChildren().addAll(transparentParts);

    // KEREKEK
    PhongMaterial wheelMat = new PhongMaterial(Color.web("#111111"));
    PhongMaterial rimMat = new PhongMaterial(Color.LIGHTGRAY);
    double[] wheelX = {-l * 0.3, l * 0.3}; double[] wheelZ = {-w / 2, w / 2};
    for (double x : wheelX) {
      for (double z : wheelZ) {
        Group wheelGroup = new Group();
        Cylinder tire = new Cylinder(wr, wt); tire.setMaterial(wheelMat); tire.setDrawMode(dMode);
        Cylinder rim = new Cylinder(wr * 0.5, wt + 0.2); rim.setMaterial(rimMat); rim.setDrawMode(dMode);
        Box spoke = new Box(wr * 0.8, wt + 0.5, wr * 0.15); spoke.setMaterial(new PhongMaterial(Color.WHITE)); spoke.setDrawMode(dMode);
        wheelGroup.getChildren().addAll(tire, rim, spoke);

        Rotate rUp = new Rotate(90, Rotate.X_AXIS);
        Rotate rRoll = new Rotate(0, Rotate.Y_AXIS);
        wheelGroup.getTransforms().addAll(rUp, rRoll);
        wheelRotations.add(rRoll);

        wheelGroup.setTranslateX(x); wheelGroup.setTranslateY(150 - wr); wheelGroup.setTranslateZ(z);
        wheelsGroup.getChildren().add(wheelGroup);
      }
    }

    // NIGHT MODE VILÁGÍTÁS
    if (isNight) {
      Pane parent = (Pane) pane3D.getParent();
      if(parent != null) parent.setStyle("-fx-background-color: #050510;");
      ambientLight.setColor(Color.rgb(15, 15, 20));
      sunLight.setLightOn(false);

      SpotLight leftBeam = new SpotLight(Color.web("#FFFFCC"));
      leftBeam.setTranslateX(-l * 0.5 - 2); leftBeam.setTranslateY(originY + h * 0.3); leftBeam.setTranslateZ(-w * 0.3);
      leftBeam.setDirection(new Point3D(-1, 0.2, 0)); leftBeam.setInnerAngle(20); leftBeam.setOuterAngle(50); leftBeam.setFalloff(0.2);

      SpotLight rightBeam = new SpotLight(Color.web("#FFFFCC"));
      rightBeam.setTranslateX(-l * 0.5 - 2); rightBeam.setTranslateY(originY + h * 0.3); rightBeam.setTranslateZ(w * 0.3);
      rightBeam.setDirection(new Point3D(-1, 0.2, 0)); rightBeam.setInnerAngle(20); rightBeam.setOuterAngle(50); rightBeam.setFalloff(0.2);

      PointLight interiorLight = new PointLight(Color.web("#FFDD88", 0.8));
      interiorLight.setTranslateX(frontX + lBody/2.0); interiorLight.setTranslateY(originY - h*0.2); interiorLight.setTranslateZ(0);

      chassisGroup.getChildren().addAll(leftBeam, rightBeam, interiorLight);
    } else {
      Pane parent = (Pane) pane3D.getParent();
      if(parent != null) parent.setStyle("-fx-background-color: #87CEEB;");
      ambientLight.setColor(Color.rgb(150, 150, 150));
      sunLight.setLightOn(true);
    }
  }

  private void setupSlidersAndInputs() {
    lengthSlider.valueProperty().addListener((o, ov, nv) -> attemptDimensionChange(nv.doubleValue(), currentBus.getHeight(), currentBus.getWidth(), currentBus.getWheelRadius(), currentBus.getWheelThickness(), currentBus.getWindowCount()));
    heightSlider.valueProperty().addListener((o, ov, nv) -> attemptDimensionChange(currentBus.getLength(), nv.doubleValue(), currentBus.getWidth(), currentBus.getWheelRadius(), currentBus.getWheelThickness(), currentBus.getWindowCount()));
    widthSlider.valueProperty().addListener((o, ov, nv) -> attemptDimensionChange(currentBus.getLength(), currentBus.getHeight(), nv.doubleValue(), currentBus.getWheelRadius(), currentBus.getWheelThickness(), currentBus.getWindowCount()));
    wheelRadiusSlider.valueProperty().addListener((o, ov, nv) -> attemptDimensionChange(currentBus.getLength(), currentBus.getHeight(), currentBus.getWidth(), nv.doubleValue(), currentBus.getWheelThickness(), currentBus.getWindowCount()));
    wheelThicknessSlider.valueProperty().addListener((o, ov, nv) -> attemptDimensionChange(currentBus.getLength(), currentBus.getHeight(), currentBus.getWidth(), currentBus.getWheelRadius(), nv.doubleValue(), currentBus.getWindowCount()));
    windowCountSlider.valueProperty().addListener((o, ov, nv) -> attemptDimensionChange(currentBus.getLength(), currentBus.getHeight(), currentBus.getWidth(), currentBus.getWheelRadius(), currentBus.getWheelThickness(), nv.intValue()));

    colorPicker.setOnAction(e -> {
      if (!isUpdatingUI) {
        currentBus.setColorHex(toHexString(colorPicker.getValue()));
        updateUI();
      }
    });
  }

  private String toHexString(Color color) {
    return String.format("#%02X%02X%02X", (int)(color.getRed() * 255), (int)(color.getGreen() * 255), (int)(color.getBlue() * 255));
  }

  private void setupCanvasInteractivity() {
    canvas.setOnMouseMoved(e -> {
      double mx = e.getX(), my = e.getY();
      double l = currentBus.getLength(), h = currentBus.getHeight(), w = currentBus.getWidth();
      double wr = currentBus.getWheelRadius(), wt = currentBus.getWheelThickness();

      boolean hoverL = Math.hypot(mx - (sideX + l), my - (sideY + h / 2)) < 15;
      boolean hoverH = Math.hypot(mx - (sideX + l / 2), my - (sideY + h)) < 15;
      boolean hoverW = Math.hypot(mx - (topX + l / 2), my - (topY + w)) < 15;
      boolean hoverWR = Math.hypot(mx - (sideX + l * 0.85), my - (sideY + h - wr * 2)) < 15;
      boolean hoverWT = Math.hypot(mx - (topX + l * 0.85), my - (topY - wt)) < 15;

      if (hoverL) canvas.setCursor(Cursor.E_RESIZE);
      else if (hoverH || hoverW) canvas.setCursor(Cursor.S_RESIZE);
      else if (hoverWR || hoverWT) canvas.setCursor(Cursor.N_RESIZE);
      else canvas.setCursor(Cursor.DEFAULT);
    });

    canvas.setOnMousePressed(e -> {
      double mx = e.getX(), my = e.getY();
      double l = currentBus.getLength(), h = currentBus.getHeight(), w = currentBus.getWidth();
      double wr = currentBus.getWheelRadius(), wt = currentBus.getWheelThickness();

      if (Math.hypot(mx - (sideX + l), my - (sideY + h / 2)) < 15) activeDragMode = DragMode.LENGTH;
      else if (Math.hypot(mx - (sideX + l / 2), my - (sideY + h)) < 15) activeDragMode = DragMode.HEIGHT;
      else if (Math.hypot(mx - (topX + l / 2), my - (topY + w)) < 15) activeDragMode = DragMode.WIDTH;
      else if (Math.hypot(mx - (sideX + l * 0.85), my - (sideY + h - wr * 2)) < 15) activeDragMode = DragMode.WHEEL_RADIUS;
      else if (Math.hypot(mx - (topX + l * 0.85), my - (topY - wt)) < 15) activeDragMode = DragMode.WHEEL_THICKNESS;
    });

    canvas.setOnMouseDragged(e -> {
      if (activeDragMode == DragMode.NONE) return;

      double nL = currentBus.getLength(), nH = currentBus.getHeight(), nW = currentBus.getWidth();
      double nWR = currentBus.getWheelRadius(), nWT = currentBus.getWheelThickness();
      double h = currentBus.getHeight();

      if (activeDragMode == DragMode.LENGTH) nL = e.getX() - sideX;
      else if (activeDragMode == DragMode.HEIGHT) nH = e.getY() - sideY;
      else if (activeDragMode == DragMode.WIDTH) nW = e.getY() - topY;
      else if (activeDragMode == DragMode.WHEEL_RADIUS) nWR = (sideY + h - e.getY()) / 2.0;
      else if (activeDragMode == DragMode.WHEEL_THICKNESS) nWT = topY - e.getY();

      if (snapToGridCheck.isSelected()) {
        if (activeDragMode == DragMode.LENGTH || activeDragMode == DragMode.HEIGHT || activeDragMode == DragMode.WIDTH) {
          nL = Math.round(nL / 10.0) * 10; nH = Math.round(nH / 10.0) * 10; nW = Math.round(nW / 10.0) * 10;
        } else {
          nWR = Math.round(nWR / 5.0) * 5; nWT = Math.round(nWT / 5.0) * 5;
        }
      }
      attemptDimensionChange(nL, nH, nW, nWR, nWT, currentBus.getWindowCount());
    });

    canvas.setOnMouseReleased(e -> activeDragMode = DragMode.NONE);
  }

  private void attemptDimensionChange(double l, double h, double w, double wr, double wt, int wc) {
    if (isUpdatingUI) return;
    currentBus.setDimensions(l, h, w, wr, wt, wc);
    updateUI();
  }

  private void updateUI() {
    isUpdatingUI = true;
    lengthSlider.setValue(currentBus.getLength());
    heightSlider.setValue(currentBus.getHeight());
    widthSlider.setValue(currentBus.getWidth());
    wheelRadiusSlider.setValue(currentBus.getWheelRadius());
    wheelThicknessSlider.setValue(currentBus.getWheelThickness());
    windowCountSlider.setValue(currentBus.getWindowCount());
    colorPicker.setValue(Color.web(currentBus.getColorHex()));
    isUpdatingUI = false;

    idLabel.setText("ID: " + currentBus.getId());
    volumeLabel.setText(String.format("Térfogat: %.2f", currentBus.getVolume()));
    surfaceLabel.setText(String.format("Felszín: %.2f", currentBus.getSurfaceArea()));

    draw2DBus();
    build3DModel();
  }

  private double mapX2D(double x3d, double l) { return sideX + (x3d + l * 0.5); }

  // FÁJLKEZELÉS

  private void loadRecentFilesFromPrefs() {
    String saved = prefs.get(RECENT_FILES_KEY, "");
    if (!saved.isEmpty()) {
      recentFilePaths = new ArrayList<>(Arrays.asList(saved.split(";")));
    }
  }

  private void saveRecentFilesToPrefs() {
    prefs.put(RECENT_FILES_KEY, String.join(";", recentFilePaths));
  }

  private void addRecentFile(String path) {
    recentFilePaths.remove(path);
    recentFilePaths.add(0, path);
    if (recentFilePaths.size() > 5) {
      recentFilePaths.remove(recentFilePaths.size() - 1);
    }
    saveRecentFilesToPrefs();
    updateRecentFilesMenu();
  }

  private void removeRecentFile(String path) {
    recentFilePaths.remove(path);
    saveRecentFilesToPrefs();
    updateRecentFilesMenu();
  }

  private void updateRecentFilesMenu() {
    if (recentFilesMenu == null) return;
    recentFilesMenu.getItems().clear();

    if (recentFilePaths.isEmpty()) {
      MenuItem empty = new MenuItem("Nincsenek legutóbbi fájlok");
      empty.setDisable(true);
      recentFilesMenu.getItems().add(empty);
      return;
    }

    for (String path : recentFilePaths) {
      MenuItem item = new MenuItem(new File(path).getName());
      item.setOnAction(e -> loadFromFile(new File(path)));
      recentFilesMenu.getItems().add(item);
    }
  }

  @FXML public void handleGenerate() { currentBus.randomize(); updateUI(); }

  @FXML
  public void handleDelete() {
    currentBus = new SchoolBus();

    isUpdatingUI = true;
    snapToGridCheck.setSelected(false);
    autoRotateCheck.setSelected(false);
    driveModeCheck.setSelected(false);
    nightModeCheck.setSelected(false);
    wireframeCheck.setSelected(false);
    stopSignCheck.setSelected(false);
    doorOpenCheck.setSelected(false);
    isUpdatingUI = false;

    handleResetCamera();

    updateUI();
  }

  @FXML public void handleSave() {
    currentBus.setId("bus_" + java.util.UUID.randomUUID().toString().substring(0, 5));
    updateUI();

    FileChooser fileChooser = new FileChooser();
    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Fájlok (*.json)", "*.json"));
    fileChooser.setInitialFileName(currentBus.getId() + ".json");

    File currentDir = new File(System.getProperty("user.dir"));
    if (currentDir.exists() && currentDir.isDirectory()) {
      fileChooser.setInitialDirectory(currentDir);
    }

    File file = fileChooser.showSaveDialog(canvas.getScene().getWindow());

    if (file != null) {
      try {
        StorageManager.saveBus(currentBus, file);
        addRecentFile(file.getAbsolutePath());
      } catch (Exception ex) {
        ex.printStackTrace();
        showErrorDialog("Hiba a mentés során", "Nem sikerült elmenteni a fájlt: " + file.getName());
      }
    }
  }

  @FXML public void handleLoad() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Fájlok (*.json)", "*.json"));

    File currentDir = new File(System.getProperty("user.dir"));
    if (currentDir.exists() && currentDir.isDirectory()) {
      fileChooser.setInitialDirectory(currentDir);
    }

    File file = fileChooser.showOpenDialog(canvas.getScene().getWindow());
    if (file != null) {
      loadFromFile(file);
    }
  }

  private void loadFromFile(File file) {
    if (!file.exists()) {
      showErrorDialog("Fájl nem található", "A fájl már nem létezik: " + file.getName() + "\nA rendszer eltávolítja a legutóbbi fájlok listájáról.");
      removeRecentFile(file.getAbsolutePath());
      return;
    }
    try {
      currentBus = StorageManager.loadBus(file);
      addRecentFile(file.getAbsolutePath());
      updateUI();
    } catch (Exception ex) {
      ex.printStackTrace();
      showErrorDialog("Hiba a betöltés során", "Nem sikerült betölteni vagy értelmezni a fájlt: " + file.getName());
      removeRecentFile(file.getAbsolutePath());
    }
  }

  private void showErrorDialog(String title, String content) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(content);
    alert.showAndWait();
  }

  //  SÚGÓ

  @FXML public void handleShowHelp() {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("School Bus Modeler - Súgó");
    alert.setHeaderText("Részletes Használati Útmutató");

    VBox vbox = new VBox(12);
    vbox.setPadding(new javafx.geometry.Insets(10));

    Label title1 = new Label("🖱️ 3D Kamera és 2D Interakció");
    title1.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #007acc;");
    Label text1 = new Label("• 3D Nézet: Bal egérgombbal forgathatod a kamerát, a jobb egérgombbal (vagy középső görgővel) eltolhatod (pan). Görgővel nagyíthatsz.\n" +
        "• 2D Tervrajz: Az egeret a busz széleihez vagy a kerekekhez húzva megjelennek a méretező fogópontok. Ezekkel közvetlenül a rajzon méretezheted a buszt.");
    text1.setWrapText(true);

    Label title2 = new Label("💾 Fájlkezelés és Fast Load");
    title2.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #007acc;");
    Label text2 = new Label("• Mentés (Save): Minden mentésnél automatikusan új, egyedi azonosítót (ID) kap a busz, így elkerülhető a véletlen felülírás.\n" +
        "• Betöltés (Load): Csak érvényes JSON fájlokat enged kiválasztani a tallózó.\n" +
        "• Legutóbbi fájlok: A rendszer megjegyzi az utolsó 5 mentett vagy betöltött modellt. Innen egy kattintással (Fast Load) betölthetőek. Ha egy fájl időközben törlődött, a program érzékeli és eltávolítja a listából.");
    text2.setWrapText(true);

    Label title3 = new Label("🛠️ Eszközök és Extrák (Tools)");
    title3.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #007acc;");
    Label text3 = new Label("• Rácshoz igazítás: Bekapcsolásakor a 2D-s kézi méretezés kerek számokhoz igazodik.\n" +
        "• Vezetési mód: Élő szimuláció! A busz valósághűen rugózik a kasztnin, a kerekek gurulnak, a 2D nézetben pedig füstkarikák szállnak fel a kipufogóból.\n" +
        "• Éjszakai mód: Éjszakai környezet, felkapcsolódó ragyogó halogén fényszórókkal, belső világítással és speciális 2D tervrajz effektekkel.\n" +
        "• Stoptábla: Valósághű, kihajló amerikai stoptábla dupla villogó LED-ekkel.\n" +
        "• Utasajtó: Az ajtó kinyitásával beláthatsz a részletesen kidolgozott belső térbe, ahol látszanak a barna bőrülések, a kormánykerék és a műszerfal.");
    text3.setWrapText(true);

    vbox.getChildren().addAll(title1, text1, new Separator(), title2, text2, new Separator(), title3, text3);

    ScrollPane scroll = new ScrollPane(vbox);
    scroll.setFitToWidth(true);
    scroll.setPrefSize(500, 450);

    alert.getDialogPane().setContent(scroll);
    alert.showAndWait();
  }

  @FXML public void handleExit() { Platform.exit(); }

  //  2D RAJZOLÁS

  private void draw2DBus() {
    if (canvas.getWidth() == 0) return;
    GraphicsContext gc = canvas.getGraphicsContext2D();

    boolean isNight = nightModeCheck.isSelected();
    Color bgColor = isNight ? Color.web("#0f172a") : Color.web("#1e1e1e");
    Color gridCol = isNight ? Color.web("#1e293b") : Color.web("#2d2d30");
    Color lineCol = isNight ? Color.web("#38bdf8") : Color.web("#d4d4d4");

    gc.setFill(bgColor);
    gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

    gc.setStroke(gridCol); gc.setLineWidth(1);
    for (int i = 0; i < canvas.getWidth(); i += 50) gc.strokeLine(i, 0, i, canvas.getHeight());
    for (int i = 0; i < canvas.getHeight(); i += 50) gc.strokeLine(0, i, canvas.getWidth(), i);

    double l = currentBus.getLength(), h = currentBus.getHeight(), w = currentBus.getWidth();
    double wr = currentBus.getWheelRadius(), wt = currentBus.getWheelThickness();
    int wc = currentBus.getWindowCount();

    double frontX = sideX + l + 120;
    Color bodyCol = Color.web(currentBus.getColorHex());
    Color winCol = Color.web("#87CEEB", 0.5);
    Color handleCol = Color.web("#4fc1ff");

    if (isNight) {
      bodyCol = bodyCol.darker();
      winCol = Color.web("#fef08a", 0.5);

      // Fényszóró csóvák
      gc.setFill(Color.web("#fef08a", 0.15));
      gc.fillPolygon(new double[]{sideX, sideX - 250, sideX - 250}, new double[]{sideY + h * 0.6, sideY - 50, sideY + h + 50}, 3);
      gc.fillPolygon(new double[]{topX, topX - 250, topX - 250}, new double[]{topY, topY - 100, topY + w + 100}, 3);

      gc.setFill(Color.web("#FFFF99"));
      gc.fillOval(frontX + 10, sideY + h * 0.5, 30, 30);
      gc.fillOval(frontX + w - 40, sideY + h * 0.5, 30, 30);
      gc.setFill(Color.web("#FFFF99", 0.3));
      gc.fillOval(frontX + 5, sideY + h * 0.5 - 5, 40, 40);
      gc.fillOval(frontX + w - 45, sideY + h * 0.5 - 5, 40, 40);
    }

    // OLDALNÉZET
    gc.setStroke(lineCol); gc.setLineWidth(1.5);
    gc.setFill(lineCol); gc.fillText("OLDALNÉZET (Jobb oldal)", sideX, sideY - 20);

    gc.setFill(bodyCol);
    gc.fillRect(sideX + l * 0.2, sideY, l * 0.8, h);
    gc.fillRect(sideX, sideY + h * 0.45, l * 0.2, h * 0.55); // Egyenes szélvédő
    gc.strokeRect(sideX + l * 0.2, sideY, l * 0.8, h);
    gc.strokeRect(sideX, sideY + h * 0.45, l * 0.2, h * 0.55);

    double lBody = l * 0.8;
    double frontX_2D = sideX + l * 0.2;
    double pW = Math.max(2.0, lBody * 0.04);
    double bayW = (lBody - pW) / wc;
    double winW = bayW - pW;
    double doorW = winW;
    double headerH = h * 0.1;

    for(int i = 0; i < wc; i++) {
      double winX_2D = frontX_2D + pW + i * bayW;
      double wH = h * 0.45;
      double wY = sideY + h * 0.05;

      if (i == 0) {
        double doorH_2D = h - headerH;
        if (doorOpenCheck.isSelected()) {
          gc.setFill(isNight ? Color.web("#FFDD88", 0.6) : Color.web("#111111"));
          gc.fillRect(winX_2D, sideY + headerH, doorW, doorH_2D);
          gc.setFill(Color.DARKGRAY); gc.fillOval(winX_2D + doorW * 0.2, sideY + h * 0.4, doorW * 0.6, 5);
        } else {
          gc.setFill(winCol);
          gc.fillRect(winX_2D, sideY + headerH, doorW, doorH_2D);
          gc.strokeRect(winX_2D, sideY + headerH, doorW, doorH_2D);
        }
      } else {
        gc.setFill(Color.web("#5C3A21"));
        gc.fillRect(winX_2D + winW * 0.1, wY + wH * 0.5, winW * 0.8, wH * 0.5);

        gc.setFill(winCol);
        gc.fillRect(winX_2D, wY, winW, wH);
        gc.strokeRect(winX_2D, wY, winW, wH);

        if (!isNight && !doorOpenCheck.isSelected()) {
          gc.setFill(Color.web("#333333", 0.6));
          double pH = wH * 0.4;
          gc.fillOval(winX_2D + winW/2 - winW*0.1, wY + wH*0.5 - pH, winW*0.2, winW*0.2);
          gc.fillArc(winX_2D + winW/2 - winW*0.2, wY + wH*0.5 - pH/2, winW*0.4, pH, 0, 180, ArcType.ROUND);
        }
      }

      gc.setFill(bodyCol);
      gc.fillRect(frontX_2D, sideY, pW, h*0.45);
      if (i < wc - 1) {
        gc.fillRect(winX_2D + winW, sideY, pW, h*0.45);
      }
    }

    gc.setFill(bodyCol);
    gc.fillRect(frontX_2D + lBody - pW, sideY, pW, h);

    if (driveModeCheck.isSelected()) {
      gc.setFill(Color.web("#888888", 0.5));
      for (double[] p : smokeParticles) gc.fillOval(p[0] - p[3]/2, p[1] - p[3]/2, p[3], p[3]);
    }

    gc.setFill(Color.web("#111111"));
    gc.fillOval(sideX + l * 0.15 - wr, sideY + h - wr, wr*2, wr*2);
    gc.fillOval(sideX + l * 0.85 - wr, sideY + h - wr, wr*2, wr*2);
    gc.setStroke(Color.WHITE); gc.setLineWidth(2);
    double rad1 = Math.toRadians(current2DWheelAngle);
    gc.strokeLine(sideX + l * 0.15, sideY + h, sideX + l * 0.15 + Math.cos(rad1)*wr*0.8, sideY + h + Math.sin(rad1)*wr*0.8);
    gc.strokeLine(sideX + l * 0.85, sideY + h, sideX + l * 0.85 + Math.cos(rad1)*wr*0.8, sideY + h + Math.sin(rad1)*wr*0.8);

    // FELÜLNÉZET
    gc.setStroke(lineCol); gc.setLineWidth(1.5);
    gc.setFill(lineCol); gc.fillText("FELÜLNÉZET", topX, topY - 20);
    gc.setFill(Color.web("#111111"));
    gc.fillRect(topX + l * 0.15 - wr, topY - wt, wr*2, wt); gc.fillRect(topX + l * 0.85 - wr, topY - wt, wr*2, wt);
    gc.fillRect(topX + l * 0.15 - wr, topY + w, wr*2, wt);  gc.fillRect(topX + l * 0.85 - wr, topY + w, wr*2, wt);

    gc.setFill(bodyCol);
    gc.fillRect(topX, topY, l, w); gc.strokeRect(topX, topY, l, w);
    gc.strokeLine(topX + l * 0.2, topY, topX + l * 0.2, topY + w);

    if (doorOpenCheck.isSelected()) {
      gc.setFill(winCol);
      double doorX_2D = mapX2D(-l * 0.3 + pW + bayW / 2.0, l) - doorW / 2.0;
      gc.fillRect(doorX_2D, topY - 20, doorW, 20);
    }

    // ELÖLNÉZET ÉS KORMÁNY
    gc.setFill(lineCol); gc.fillText("ELÖLNÉZET", frontX, sideY - 20);
    gc.setFill(bodyCol);
    gc.fillRect(frontX, sideY, w, h); gc.strokeRect(frontX, sideY, w, h);
    gc.fillRect(frontX, sideY + h * 0.45, w, h * 0.55); gc.strokeRect(frontX, sideY + h * 0.45, w, h * 0.55);

    double wsX = frontX + 10; double wsY = sideY + 10;
    double wsW = w - 20; double wsH = h * 0.45 - 10;
    gc.setFill(winCol);
    gc.fillRect(wsX, wsY, wsW, wsH); gc.strokeRect(wsX, wsY, wsW, wsH);

    gc.setFill(Color.web("#222222", 0.9));
    double steerW = wsW * 0.3;
    double steerX = wsX + wsW * 0.7 - steerW/2;
    double steerY = wsY + wsH - 10;
    gc.fillRect(wsX, wsY + wsH - 5, wsW, 5);
    gc.fillOval(steerX, steerY, steerW, 15);
    gc.fillRect(steerX + steerW/2 - 3, steerY + 5, 6, 20);

    gc.setFill(Color.web("#111111"));
    gc.fillRect(frontX - wt, sideY + h - wr, wt, wr*2); gc.fillRect(frontX + w, sideY + h - wr, wt, wr*2);

    // STOP TÁBLA 2D
    double stopR = h * 0.12;
    double stopDiam = stopR * 2;
    double stopBaseX_2D = frontX_2D + pW + bayW + pW/2.0;

    if (stopSignCheck.isSelected()) {
      gc.setFill(Color.RED); gc.setStroke(Color.WHITE); gc.setLineWidth(2);
      gc.fillRect(frontX + w + stopR, sideY + h * 0.5, stopDiam, stopDiam);
      gc.strokeRect(frontX + w + stopR, sideY + h * 0.5, stopDiam, stopDiam);
      gc.setFill(Color.WHITE); gc.setFont(Font.font("Arial", FontWeight.BOLD, stopR * 0.6));
      gc.fillText("STOP", frontX + w + stopR + 2, sideY + h * 0.5 + stopR + 4);

      gc.fillRect(stopBaseX_2D - 3, topY + w + stopR, 6, stopDiam);
      gc.fillRect(stopBaseX_2D - 3, sideY + h * 0.5, 6, stopDiam);
    } else {
      gc.setFill(Color.RED);
      gc.fillRect(frontX + w, sideY + h * 0.5, 6, stopDiam);
      gc.fillRect(stopBaseX_2D, topY + w, stopDiam, 6);

      gc.setStroke(Color.WHITE); gc.setLineWidth(2);
      gc.fillRect(stopBaseX_2D, sideY + h * 0.5, stopDiam, stopDiam);
      gc.strokeRect(stopBaseX_2D, sideY + h * 0.5, stopDiam, stopDiam);
      gc.setFill(Color.WHITE); gc.setFont(Font.font("Arial", FontWeight.BOLD, stopR * 0.6));
      gc.fillText("STOP", stopBaseX_2D + 2, sideY + h * 0.5 + stopR + 4);
    }

    // FOGÓPONTOK ÉS MÉRETEK
    gc.setStroke(handleCol); gc.setFill(handleCol); gc.setLineWidth(1);
    gc.strokeLine(sideX, sideY - 5, sideX + l, sideY - 5); gc.fillText("L: " + Math.round(l), sideX + l / 2 - 15, sideY - 10);
    gc.fillOval(sideX + l - 6, sideY + h / 2 - 6, 12, 12);
    gc.strokeLine(sideX - 5, sideY, sideX - 5, sideY + h); gc.fillText("H: " + Math.round(h), sideX - 45, sideY + h / 2);
    gc.fillOval(sideX + l / 2 - 6, sideY + h - 6, 12, 12);
    gc.strokeLine(topX - 5, topY, topX - 5, topY + w); gc.fillText("W: " + Math.round(w), topX - 45, topY + w / 2);
    gc.fillOval(topX + l / 2 - 6, topY + w - 6, 12, 12);

    gc.setFill(Color.web("#ff66cc"));
    gc.fillOval(sideX + l * 0.85 - 6, sideY + h - wr*2 - 6, 12, 12); gc.fillText("R: " + Math.round(wr), sideX + l * 0.85 + 10, sideY + h - wr);
    gc.fillOval(topX + l * 0.85 - 6, topY - wt - 6, 12, 12); gc.fillText("T: " + Math.round(wt), topX + l * 0.85 + 10, topY - wt / 2);
  }
}