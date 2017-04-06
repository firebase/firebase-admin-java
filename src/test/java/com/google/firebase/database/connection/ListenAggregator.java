package com.google.firebase.database.connection;

import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.core.CoreTestHelpers;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.Repo;
import com.google.firebase.database.core.view.QueryParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class ListenAggregator {

  public static List<String> dumpListens(final Repo repo, Path path) throws InterruptedException {
    final List<PersistentConnection> conns = new ArrayList<>(1);
    final Semaphore semaphore = new Semaphore(0);
    // we need to run this through our work queue to make the ordering work out.
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            conns.add(CoreTestHelpers.getRepoConnection(repo));
            semaphore.release(1);
          }
        });
    TestHelpers.waitFor(semaphore);
    conns.get(0);
    List<List<String>> pathList = new ArrayList<>();
    List<Map<String, Object>> queryParamList = new ArrayList<>();
    // TODO(dimond): Find a way to actually get listens, or not test against internal state?
    //conn.getListens(pathList, queryParamList);

    Map<String, List<String>> pathToQueryParamStrings = new HashMap<>();
    for (int i = 0; i < pathList.size(); i++) {
      Path queryPath = new Path(pathList.get(i));
      QueryParams queryParams = QueryParams.fromQueryObject(queryParamList.get(i));

      if (path.contains(queryPath)) {
        List<String> allParamsStrings = pathToQueryParamStrings.get(queryPath.toString());
        if (allParamsStrings == null) {
          allParamsStrings = new ArrayList<>();
        }
        String paramsString =
            queryParams.isDefault() ? "default" : queryParams.getWireProtocolParams()
                .toString();
        allParamsStrings.add(paramsString);
        pathToQueryParamStrings.put(queryPath.toString(), allParamsStrings);
      }
    }

    List<String> paths = new ArrayList<>(pathToQueryParamStrings.keySet());
    Collections.sort(paths);

    int prefixLength = path.getFront().asString().length() + 1; // +1 for '/'
    List<String> results = new ArrayList<>(paths.size());
    for (String listenPath : paths) {
      List<String> allParamsStrings = pathToQueryParamStrings.get(listenPath);
      Collections.sort(allParamsStrings);
      String listen = listenPath.substring(prefixLength) + ":" + allParamsStrings.toString();
      results.add(listen);
    }

    return results;
  }
}
