#include <WiFi.h>
#include <WebServer.h>

// Fill these in, then flash the ESP32-DevKitC-32E.
// The phone Settings screen must use this same access code.
const char *WIFI_SSID = "your-wifi-name";
const char *WIFI_PASSWORD = "your-wifi-password";
const char *GARAGE_TOKEN = "change-me";

const int PORT = 8080;
const int PIN_RELAY = 27;
const int PIN_SHUT = 25;
const int PIN_OPEN = 26;
const unsigned long PULSE_MS = 400;
const unsigned long IDLE_AFTER_MS = 1500;

#if __has_include(<Adafruit_VL53L1X.h>)
#define HAS_VL53 1
#include <Adafruit_VL53L1X.h>
#include <Wire.h>
Adafruit_VL53L1X distanceSensor;
#else
#define HAS_VL53 0
#endif

WebServer server(PORT);

float position = 0.0f;
const char *travel = "idle";
float previousPosition = 0.0f;
unsigned long lastMotionMs = 0;
int shutMillimeters = -1;
int openMillimeters = -1;
bool sensorReady = false;

bool shutLimit() { return digitalRead(PIN_SHUT) == LOW; }
bool openLimit() { return digitalRead(PIN_OPEN) == LOW; }

int readMillimeters() {
#if HAS_VL53
  if (!sensorReady) return -1;
  int16_t millimeters = distanceSensor.distance();
  if (millimeters < 0) return -1;
  return millimeters;
#else
  return -1;
#endif
}

void noteLimits(int millimeters) {
  if (millimeters < 0) return;
  if (shutLimit()) shutMillimeters = millimeters;
  if (openLimit()) openMillimeters = millimeters;
}

float positionFromDistance(int millimeters) {
  if (millimeters < 0 || shutMillimeters < 0 || openMillimeters < 0) return -1.0f;
  float span = static_cast<float>(shutMillimeters - openMillimeters);
  if (fabs(span) < 30.0f) return -1.0f;
  float raw = (shutMillimeters - millimeters) / span;
  if (raw < 0.0f) raw = 0.0f;
  if (raw > 1.0f) raw = 1.0f;
  return raw;
}

void updateDoor() {
  int millimeters = readMillimeters();
  noteLimits(millimeters);
  float measured = positionFromDistance(millimeters);
  if (shutLimit() && !openLimit()) {
    position = 0.0f;
  } else if (openLimit() && !shutLimit()) {
    position = 1.0f;
  } else if (measured >= 0.0f) {
    position = measured;
  } else if (!shutLimit() && !openLimit()) {
    position = 0.5f;
  }

  if (fabs(position - previousPosition) >= 0.02f) {
    travel = position > previousPosition ? "opening" : "closing";
    lastMotionMs = millis();
    previousPosition = position;
  } else if (millis() - lastMotionMs > IDLE_AFTER_MS) {
    travel = "idle";
  }
}

void pulseButton() {
  digitalWrite(PIN_RELAY, HIGH);
  delay(PULSE_MS);
  digitalWrite(PIN_RELAY, LOW);
  lastMotionMs = millis();
}

bool authorized() {
  if (GARAGE_TOKEN[0] == '\0') return false;
  String expected = String("Bearer ") + GARAGE_TOKEN;
  return server.header("Authorization") == expected;
}

String statusJson() {
  updateDoor();
  String body = "{\"position\":";
  body += String(position, 3);
  body += ",\"travel\":\"";
  body += travel;
  body += "\"}";
  return body;
}

void sendStatus() {
  server.send(200, "application/json", statusJson());
}

void handleStatus() {
  if (!authorized()) {
    server.send(401, "application/json", "{\"error\":\"unauthorized\"}");
    return;
  }
  sendStatus();
}

void handleCommand() {
  if (!authorized()) {
    server.send(401, "application/json", "{\"error\":\"unauthorized\"}");
    return;
  }
  String body = server.arg("plain");
  updateDoor();
  bool pulse = false;
  if (body.indexOf("\"open\"") >= 0) {
    pulse = !(openLimit() && strcmp(travel, "idle") == 0);
  } else if (body.indexOf("\"close\"") >= 0) {
    pulse = !(shutLimit() && strcmp(travel, "idle") == 0);
  } else if (body.indexOf("\"stop\"") >= 0) {
    pulse = strcmp(travel, "idle") != 0;
  } else {
    server.send(400, "application/json", "{\"error\":\"command\"}");
    return;
  }
  if (pulse) pulseButton();
  sendStatus();
}

void setup() {
  pinMode(PIN_RELAY, OUTPUT);
  digitalWrite(PIN_RELAY, LOW);
  pinMode(PIN_SHUT, INPUT_PULLUP);
  pinMode(PIN_OPEN, INPUT_PULLUP);

#if HAS_VL53
  Wire.begin(21, 22);
  sensorReady = distanceSensor.begin(0x29, &Wire);
  if (sensorReady) distanceSensor.startRanging();
#endif

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  unsigned long started = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - started < 20000) {
    delay(250);
  }

  const char *headerKeys[] = {"Authorization"};
  server.collectHeaders(headerKeys, 1);
  server.on("/status", HTTP_GET, handleStatus);
  server.on("/command", HTTP_POST, handleCommand);
  server.begin();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    WiFi.reconnect();
    delay(500);
    return;
  }
  server.handleClient();
  updateDoor();
}
