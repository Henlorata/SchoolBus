package com.example.schoolbus.io;

import com.example.schoolbus.model.SchoolBus;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Handles saving and loading SchoolBus objects to/from JSON files.
 */
public class StorageManager {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  /**
   * Saves a SchoolBus instance to the specified file.
   */
  public static void saveBus(SchoolBus bus, File file) throws IOException {
    try (FileWriter writer = new FileWriter(file)) {
      GSON.toJson(bus, writer);
    }
  }

  /**
   * Loads a SchoolBus instance from the specified file.
   */
  public static SchoolBus loadBus(File file) throws IOException {
    try (FileReader reader = new FileReader(file)) {
      return GSON.fromJson(reader, SchoolBus.class);
    }
  }
}