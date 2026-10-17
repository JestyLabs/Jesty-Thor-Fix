package com.thor.displaypowertest;
final class DashboardSettingsController {
 interface Feedback {void displayCommandStarted();void displayCommandFinished(String e,boolean u);void cpuFixCommandStarted();}
 boolean pending=true,inFlight;void setActivationPending(boolean b){pending=b;}boolean displayCommandInFlight(){return inFlight;}
 boolean desiredDisplayFix(){return true;}boolean desiredCpuFix(){return false;}boolean desiredLidGuard(){return false;}
 void syncFromTelemetry(boolean held,boolean ready){} }
