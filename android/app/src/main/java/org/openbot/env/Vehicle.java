package org.openbot.env;

import android.content.Context;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class Vehicle {

  private final Noise noise = new Noise(1000, 2000, 5000);
  private boolean noiseEnabled = false;
  private int indicator = 0;
  private int speedMultiplier = 192; // 128,192,255
  private Control control = new Control(0, 0, speedMultiplier);

  private final SensorReading batteryVoltage = new SensorReading();
  private final SensorReading leftWheelTicks = new SensorReading();
  private final SensorReading rightWheelTicks = new SensorReading();
  private final SensorReading sonarReading = new SensorReading();

  private static final int diskHoles = 20;
  private static final int millisPerMinute = 60000;

  private UsbConnection usbConnection;
  protected boolean usbConnected;
  private final Context context;
  private final int baudRate;

  public Vehicle(Context context, int baudRate) {
    this.context = context;
    this.baudRate = baudRate;
    connectUsb();
  }


  public float getBatteryVoltage() {
    return batteryVoltage.getReading();
  }

  public int getBatteryPercentage() {
    return (int) ((batteryVoltage.getReading() - 9.6f) * 100 / (12.0f - 9.6f));
  }

  public void setBatteryVoltage(float batteryVoltage) {
    this.batteryVoltage.setReading(batteryVoltage);
  }

  public float getLeftWheelTicks() {
    return leftWheelTicks.getReading();
  }

  public float getLeftWheelRPM() {
    if (leftWheelTicks.getAge() > 0 && leftWheelTicks.getReading() != 0)
      return leftWheelTicks.getReading() * millisPerMinute / leftWheelTicks.getAge() / diskHoles;
    else return 0;
  }

  public void setLeftWheelTicks(float leftWheelTicks) {
    this.leftWheelTicks.setReading(leftWheelTicks);
  }

  public float getRightWheelTicks() {
    return rightWheelTicks.getReading();
  }

  public float getRightWheelRPM() {
    if (rightWheelTicks.getAge() > 0 && rightWheelTicks.getReading() != 0)
      return rightWheelTicks.getReading() * millisPerMinute / rightWheelTicks.getAge() / diskHoles;
    else return 0;
  }

  public void setRightWheelTicks(float rightWheelTicks) {
    this.rightWheelTicks.setReading(rightWheelTicks);
  }

  public float getRotation()
  {
    float rotation = (getControl().getLeft() - getControl().getRight()) * 180 /
            (getControl().getLeft() + getControl().getRight());
    if (Float.isNaN(rotation) || Float.isInfinite(rotation)) rotation = 0f;
    return rotation;
  }

  public int getSpeedPercent()
  {
    float throttle = (getControl().getLeft() + getControl().getRight()) / 2;
    return  Math.abs((int) (throttle * 100 / 255)); // 255 is the max speed
  }

  public String getDriveGear()
  {
    float throttle = (getControl().getLeft() + getControl().getRight()) / 2;
    if (throttle > 0) return "D";
    if (throttle < 0) return "R";
    return "P";
  }
  public float getSonarReading() {
    return sonarReading.getReading();
  }

  public void setSonarReading(float sonarReading) {
    this.sonarReading.setReading(sonarReading);
  }

  public Control getControl() {
    return control;
  }

  public void setControl(Control control) {
    this.control = control;
  }

  public void setControl(float left, float right) {
    this.control = new Control(left, right, speedMultiplier);
  }

  private Timer noiseTimer;

  private class NoiseTask extends TimerTask {
    @Override
    public void run() {
      noise.update();
      sendControl();
    }
  }

  public void startNoise() {
    noiseTimer = new Timer();
    NoiseTask noiseTask = new NoiseTask();
    noiseTimer.schedule(noiseTask, 0, 50); // no delay 50ms intervals
    noiseEnabled = true;
  }

  public void stopNoise() {
    noiseEnabled = false;
    noiseTimer.cancel();
  }

  public int getSpeedMultiplier() {
    return speedMultiplier;
  }

  public void setSpeedMultiplier(int speedMultiplier) {
    this.speedMultiplier = speedMultiplier;
  }

  public int getIndicator() {
    return indicator;
  }

  public void setIndicator(int indicator) {
    this.indicator = indicator;
    sendStringToUsb(String.format(Locale.US, "i%d\n", indicator));
  }

  public UsbConnection getUsbConnection() {
    return usbConnection;
  }

  public void connectUsb() {
    usbConnection = new UsbConnection(context, baudRate);
    usbConnected = usbConnection.startUsbConnection();
  }

  public void disconnectUsb() {
    Objects.requireNonNull(usbConnection)
        .send(
            String.format(
                Locale.US,
                "c%d,%d\n",
                (int) (getControl().getLeft()),
                (int) (getControl().getRight())));

    Objects.requireNonNull(usbConnection).stopUsbConnection();
    usbConnection = null;
    usbConnected = false;
  }

  public boolean isUsbConnected() {
    return usbConnected;
  }

  private void sendStringToUsb(String message) {
    Objects.requireNonNull(usbConnection).send(message);
  }

  public void sendControl() {
    int left = (int) (control.getLeft() * speedMultiplier);
    int right = (int) (control.getRight() * speedMultiplier);
    if (noiseEnabled && noise.getDirection() < 0)
      left = (int) ((control.getLeft() - noise.getValue()) * speedMultiplier);
    if (noiseEnabled && noise.getDirection() > 0)
      right = (int) ((control.getRight() - noise.getValue()) * speedMultiplier);
    sendStringToUsb(String.format(Locale.US, "c%d,%d\n", left, right));
  }
}
