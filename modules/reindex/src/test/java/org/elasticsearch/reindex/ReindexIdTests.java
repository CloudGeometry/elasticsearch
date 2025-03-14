/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.reindex;

import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ProjectState;
import org.elasticsearch.cluster.metadata.ComponentTemplate;
import org.elasticsearch.cluster.metadata.ComposableIndexTemplate;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.metadata.ProjectMetadata;
import org.elasticsearch.cluster.metadata.Template;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexMode;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.IndexVersion;
import org.elasticsearch.index.reindex.AbstractAsyncBulkByScrollActionTestCase;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.index.reindex.ScrollableHitSource;
import org.elasticsearch.xcontent.XContentType;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Reindex tests for picking ids.
 */
public class ReindexIdTests extends AbstractAsyncBulkByScrollActionTestCase<ReindexRequest, BulkByScrollResponse> {
    public void testEmptyStateCopiesId() throws Exception {
        final ProjectState projectState = ClusterState.EMPTY_STATE.projectState(Metadata.DEFAULT_PROJECT_ID);
        assertThat(action(projectState).buildRequest(doc()).getId(), equalTo(doc().getId()));
    }

    public void testStandardIndexCopiesId() throws Exception {
        assertThat(action(stateWithIndex(standardSettings())).buildRequest(doc()).getId(), equalTo(doc().getId()));
    }

    public void testTsdbIndexClearsId() throws Exception {
        assertThat(action(stateWithIndex(tsdbSettings())).buildRequest(doc()).getId(), nullValue());
    }

    public void testMissingIndexWithStandardTemplateCopiesId() throws Exception {
        assertThat(action(stateWithTemplate(standardSettings())).buildRequest(doc()).getId(), equalTo(doc().getId()));
    }

    public void testMissingIndexWithTsdbTemplateClearsId() throws Exception {
        assertThat(action(stateWithTemplate(tsdbSettings())).buildRequest(doc()).getId(), nullValue());
    }

    private ProjectState stateWithTemplate(Settings.Builder settings) {
        final var projectId = randomProjectIdOrDefault();
        ProjectMetadata.Builder projectBuilder = ProjectMetadata.builder(projectId);
        Template template = new Template(settings.build(), null, null);
        if (randomBoolean()) {
            projectBuilder.put("c", new ComponentTemplate(template, null, null));
            projectBuilder.put(
                "c",
                ComposableIndexTemplate.builder().indexPatterns(List.of("dest_index")).componentTemplates(List.of("c")).build()
            );
        } else {
            projectBuilder.put("c", ComposableIndexTemplate.builder().indexPatterns(List.of("dest_index")).template(template).build());
        }
        return ClusterState.builder(ClusterState.EMPTY_STATE).putProjectMetadata(projectBuilder).build().projectState(projectId);
    }

    private ProjectState stateWithIndex(Settings.Builder settings) {
        final var projectId = randomProjectIdOrDefault();
        IndexMetadata.Builder meta = IndexMetadata.builder(request().getDestination().index())
            .settings(settings.put(IndexMetadata.SETTING_VERSION_CREATED, IndexVersion.current()))
            .numberOfReplicas(0)
            .numberOfShards(1);
        return ClusterState.builder(ClusterState.EMPTY_STATE)
            .putProjectMetadata(ProjectMetadata.builder(projectId).put(meta))
            .build()
            .projectState(projectId);
    }

    private Settings.Builder standardSettings() {
        if (randomBoolean()) {
            return Settings.builder();
        }
        return Settings.builder().put(IndexSettings.MODE.getKey(), IndexMode.STANDARD);
    }

    private Settings.Builder tsdbSettings() {
        return Settings.builder()
            .put(IndexSettings.MODE.getKey(), IndexMode.TIME_SERIES)
            .put(IndexMetadata.INDEX_ROUTING_PATH.getKey(), "foo");
    }

    private ScrollableHitSource.BasicHit doc() {
        return new ScrollableHitSource.BasicHit("index", "id", -1).setSource(new BytesArray("{}"), XContentType.JSON);
    }

    @Override
    protected ReindexRequest request() {
        return new ReindexRequest().setDestIndex("dest_index");
    }

    private Reindexer.AsyncIndexBySearchAction action(ProjectState state) {
        return new Reindexer.AsyncIndexBySearchAction(task, logger, null, null, threadPool, null, state, null, request(), listener());
    }
}
