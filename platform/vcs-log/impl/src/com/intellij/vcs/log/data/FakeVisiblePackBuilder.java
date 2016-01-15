/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogHashMap;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.GraphColorManagerImpl;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.collapsing.CollapsedController;
import com.intellij.vcs.log.graph.impl.facade.BaseController;
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class FakeVisiblePackBuilder {
  @NotNull private final VcsLogHashMap myHashMap;

  public FakeVisiblePackBuilder(@NotNull VcsLogHashMap hashMap) {
    myHashMap = hashMap;
  }

  @NotNull
  public VisiblePack build(@NotNull VisiblePack visiblePack) {
    if (visiblePack.getVisibleGraph() instanceof VisibleGraphImpl) {
      return build(visiblePack.getDataPack(), ((VisibleGraphImpl<Integer>)visiblePack.getVisibleGraph()), visiblePack.getFilters());
    }
    else {
      VisibleGraph<Integer> newGraph = EmptyVisibleGraph.getInstance();
      DataPackBase newPack = new DataPackBase(visiblePack.getDataPack().getLogProviders(), createEmptyRefsModel(), false);
      return new VisiblePack(newPack, newGraph, true, visiblePack.getFilters());
    }
  }

  @NotNull
  private VisiblePack build(@NotNull DataPackBase oldPack, @NotNull VisibleGraphImpl<Integer> oldGraph, @NotNull VcsLogFilterCollection filters) {
    final PermanentGraphInfo<Integer> info = oldGraph.buildSimpleGraphInfo();
    Set<Integer> heads = ContainerUtil.map2Set(info.getPermanentGraphLayout().getHeadNodeIndex(), new Function<Integer, Integer>() {
      @Override
      public Integer fun(Integer integer) {
        return info.getPermanentCommitsInfo().getCommitId(integer);
      }
    });

    RefsModel newRefsModel = createRefsModel(oldPack.getRefsModel(), heads, oldGraph);
    DataPackBase newPack = new DataPackBase(oldPack.getLogProviders(), newRefsModel, false);

    GraphColorManagerImpl colorManager =
      new GraphColorManagerImpl(newRefsModel, DataPack.createHashGetter(myHashMap), DataPack.getRefManagerMap(oldPack.getLogProviders()));

    VisibleGraph<Integer> newGraph =
      new VisibleGraphImpl<Integer>(new CollapsedController(new BaseController(info), info, null), info, colorManager);

    return new VisiblePack(newPack, newGraph, true, filters);
  }

  @NotNull
  private RefsModel createEmptyRefsModel() {
    return new RefsModel(ContainerUtil.<VirtualFile, Set<VcsRef>>newHashMap(), ContainerUtil.<Integer>newHashSet(), myHashMap);
  }

  private RefsModel createRefsModel(@NotNull RefsModel refsModel,
                                    @NotNull Set<Integer> heads,
                                    @NotNull VisibleGraph<Integer> visibleGraph) {
    Map<VirtualFile, Set<VcsRef>> refs = ContainerUtil.newHashMap();
    Collection<VcsRef> allRefs = refsModel.getAllRefs();
    for (VcsRef ref : allRefs) {
      int commitIndex = myHashMap.getCommitIndex(ref.getCommitHash(), ref.getRoot());
      if (ref.getType().isBranch() || heads.contains(commitIndex)) {
        Integer row = visibleGraph.getVisibleRowIndex(commitIndex);
        if (row != null && row >= 0) {
          Set<VcsRef> refsByRoot = refs.get(ref.getRoot());
          if (refsByRoot == null) {
            refsByRoot = ContainerUtil.newHashSet();
            refs.put(ref.getRoot(), refsByRoot);
          }
          refsByRoot.add(ref);
        }
      }
    }
    return new RefsModel(refs, heads, myHashMap);
  }
}
