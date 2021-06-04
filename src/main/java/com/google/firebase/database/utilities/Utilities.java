/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.database.utilities;

import com.google.api.core.ApiFuture;
import com.google.api.core.SettableApiFuture;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.google.common.net.UrlEscapers;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.RepoInfo;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.client.utils.URLEncodedUtils;

public class Utilities {
  private static final char[] HEX_CHARACTERS = "0123456789abcdef".toCharArray();

  public static ParsedUrl parseUrl(String url) throws DatabaseException {
    try {
      URI uri = URI.create(url);

      String scheme = uri.getScheme();
      if (scheme == null) {
        throw new IllegalArgumentException("Database URL does not specify a URL scheme");
      }

      String host = uri.getHost();
      if (host == null) {
        throw new IllegalArgumentException("Database URL does not specify a valid host");
      }

      RepoInfo repoInfo = new RepoInfo();
      repoInfo.host = host.toLowerCase();
      repoInfo.secure = scheme.equals("https") || scheme.equals("wss");

      int port = uri.getPort();
      if (port != -1) {
        repoInfo.host += ":" + port;
      }

      Map<String, String> params = getQueryParamsMap(uri.getRawQuery());
      String namespaceParam = params.get("ns");
      if (!Strings.isNullOrEmpty(namespaceParam)) {
        repoInfo.namespace = namespaceParam;
      } else {
        String[] parts = host.split("\\.", -1);
        repoInfo.namespace = parts[0].toLowerCase();
      }

      repoInfo.internalHost = repoInfo.host;
      // use raw (encoded) path for backwards compatibility.
      String pathString = uri.getRawPath();
      pathString = pathString.replace("+", " ");
      Validation.validateRootPathString(pathString);

      ParsedUrl parsedUrl = new ParsedUrl();
      parsedUrl.path = new Path(pathString);
      parsedUrl.repoInfo = repoInfo;

      return parsedUrl;
    } catch (Exception e) {
      throw new DatabaseException("Invalid Firebase Database url specified: " + url, e);
    }
  }

  /**
   * Extracts a map of query parameters from an encoded query string. Repeated parameters have
   * values concatenated with commas.
   *
   * @param queryString to parse params from. Must be encoded.
   * @return map of query parameters and their values.
   */
  @VisibleForTesting
  static Map<String, String> getQueryParamsMap(String queryString)
      throws UnsupportedEncodingException {
    Map<String, String> paramsMap = new HashMap<>();
    if (Strings.isNullOrEmpty(queryString)) {
      return paramsMap;
    }
    String[] paramPairs = queryString.split("&");
    for (String paramPair : paramPairs) {
      String[] pairParts = paramPair.split("=");
      // both the first and second part will be encoded now, we must decode them
      String decodedKey = URLDecoder.decode(pairParts[0], Charsets.UTF_8.name());
      String decodedValue = URLDecoder.decode(pairParts[1], Charsets.UTF_8.name());
      String runningValue = paramsMap.get(decodedKey);
      if (Strings.isNullOrEmpty(runningValue)) {
        runningValue = decodedValue;
      } else {
        runningValue += "," + decodedValue;
      }
      paramsMap.put(pairParts[0], runningValue);
    }
    return paramsMap;
  }

  public static String[] splitIntoFrames(String src, int maxFrameSize) {
    if (src.length() <= maxFrameSize) {
      return new String[] {src};
    } else {
      ArrayList<String> segs = new ArrayList<>();
      for (int i = 0; i < src.length(); i += maxFrameSize) {
        int end = Math.min(i + maxFrameSize, src.length());
        String seg = src.substring(i, end);
        segs.add(seg);
      }
      return segs.toArray(new String[segs.size()]);
    }
  }

  public static String sha1HexDigest(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      md.update(input.getBytes("UTF-8"));
      byte[] bytes = md.digest();
      return BaseEncoding.base64().encode(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Missing SHA-1 MessageDigest provider.", e);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 encoding is required for Firebase Database to run!");
    }
  }

  public static String stringHashV2Representation(String value) {
    String escaped = value;
    if (value.indexOf('\\') != -1) {
      escaped = escaped.replace("\\", "\\\\");
    }
    if (value.indexOf('"') != -1) {
      escaped = escaped.replace("\"", "\\\"");
    }
    return '"' + escaped + '"';
  }

  public static String doubleToHashString(double value) {
    StringBuilder sb = new StringBuilder(16);
    long bits = Double.doubleToLongBits(value);
    // We use big-endian to encode the bytes
    for (int i = 7; i >= 0; i--) {
      int byteValue = (int) ((bits >>> (8 * i)) & 0xff);
      int high = ((byteValue >> 4) & 0xf);
      int low = (byteValue & 0xf);
      sb.append(HEX_CHARACTERS[high]);
      sb.append(HEX_CHARACTERS[low]);
    }
    return sb.toString();
  }

  // NOTE: We could use Ints.tryParse from guava, but I don't feel like pulling in guava (~2mb)
  // for
  // that small purpose.
  public static Integer tryParseInt(String num) {
    if (num.length() > 11 || num.length() == 0) {
      return null;
    }
    int i = 0;
    boolean negative = false;
    if (num.charAt(0) == '-') {
      if (num.length() == 1) {
        return null;
      }
      negative = true;
      i = 1;
    }
    // long to prevent overflow
    long number = 0;
    while (i < num.length()) {
      char c = num.charAt(i);
      if (c < '0' || c > '9') {
        return null;
      }
      number = number * 10 + (c - '0');
      i++;
    }
    if (negative) {
      if (-number < Integer.MIN_VALUE) {
        return null;
      } else {
        return (int) (-number);
      }
    } else {
      if (number > Integer.MAX_VALUE) {
        return null;
      }
      return (int) number;
    }
  }

  public static int compareInts(int i, int j) {
    if (i < j) {
      return -1;
    } else if (i == j) {
      return 0;
    } else {
      return 1;
    }
  }

  public static int compareLongs(long i, long j) {
    if (i < j) {
      return -1;
    } else if (i == j) {
      return 0;
    } else {
      return 1;
    }
  }

  @SuppressWarnings("unchecked")
  public static <C> C castOrNull(Object o, Class<C> clazz) {
    if (clazz.isAssignableFrom(o.getClass())) {
      return (C) o;
    } else {
      return null;
    }
  }

  @SuppressWarnings("rawtypes")
  public static <C> C getOrNull(Object o, String key, Class<C> clazz) {
    if (o == null) {
      return null;
    }
    Map map = castOrNull(o, Map.class);
    Object result = map.get(key);
    if (result != null) {
      return castOrNull(result, clazz);
    } else {
      return null;
    }
  }

  public static void hardAssert(boolean condition) {
    hardAssert(condition, "");
  }

  public static void hardAssert(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError("hardAssert failed: " + message);
    }
  }

  public static Pair<ApiFuture<Void>, DatabaseReference.CompletionListener> wrapOnComplete(
      DatabaseReference.CompletionListener optListener) {
    if (optListener == null) {
      final SettableApiFuture<Void> future = SettableApiFuture.create();
      DatabaseReference.CompletionListener listener =
          new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError error, DatabaseReference ref) {
              if (error != null) {
                future.setException(error.toException());
              } else {
                future.set(null);
              }
            }
          };
      return new Pair<ApiFuture<Void>, DatabaseReference.CompletionListener>(future, listener);
    } else {
      // If a listener is supplied we do not want to create a Task
      return new Pair<>(null, optListener);
    }
  }
}
