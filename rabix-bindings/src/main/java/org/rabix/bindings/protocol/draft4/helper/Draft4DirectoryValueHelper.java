package org.rabix.bindings.protocol.draft4.helper;

import java.io.File;
import java.util.List;

import org.rabix.common.helper.ChecksumHelper;
import org.rabix.common.helper.ChecksumHelper.HashAlgorithm;

public class Draft4DirectoryValueHelper extends Draft4BeanHelper {

  private static final String KEY_NAME = "name";
  private static final String KEY_PATH = "path";
  private static final String KEY_SIZE = "size";
  private static final String KEY_FORMAT = "format";
  private static final String KEY_CHECKSUM = "checksum";

  private static final String KEY_LISTING = "listing";
  
  public static void setDirectoryType(Object raw) {
    setValue(Draft4SchemaHelper.KEY_JOB_TYPE, Draft4SchemaHelper.TYPE_JOB_DIRECTORY, raw);
  }

  public static Object getFormat(Object raw) {
    return getValue(KEY_FORMAT, raw);
  }
  
  public static String getName(Object raw) {
    return getValue(KEY_NAME, raw);
  }

  public static void setName(String name, Object raw) {
    setValue(KEY_NAME, name, raw);
  }
  
  public static List<Object> getListing(Object raw) {
    return getValue(KEY_LISTING, raw);
  }

  public static void setListing(List<Object> listing, Object raw) {
    setValue(KEY_LISTING, listing, raw);
  }

  public static void setSize(long size, Object raw) {
    setValue(KEY_SIZE, size, raw);
  }

  public static Long getSize(Object raw) {
    Object number = getValue(KEY_SIZE, raw);
    if (number == null) {
      return null;
    }
    if (number instanceof Integer) {
      return new Long(number.toString());
    }
    return (Long) number;
  }

  public static void setChecksum(File file, Object raw, HashAlgorithm hashAlgorithm) {
    if (!file.exists()) {
      throw new RuntimeException("Missing file " + file);
    }
    String checksum = ChecksumHelper.checksum(file, hashAlgorithm);
    if (checksum != null) {
      setValue(KEY_CHECKSUM, checksum, raw);
    }
  }

  public static String getChecksum(Object raw) {
    return getValue(KEY_CHECKSUM, raw);
  }

  public static String getPath(Object raw) {
    return getValue(KEY_PATH, raw);
  }

  public static void setPath(String path, Object raw) {
    setValue(KEY_PATH, path, raw);
  }
  
}
