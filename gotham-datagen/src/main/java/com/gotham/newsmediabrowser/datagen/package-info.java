/**
 * {@code gotham-datagen} — a standalone Java console tool (NOT Spring Boot) that generates synthetic
 * journalists + articles with IMAGE/AUDIO/VIDEO and loads them into a running {@code gotham-web}
 * <em>only</em> through its HTTP CRUD ({@code POST /journalist}, {@code POST /article}). It never
 * writes to Elasticsearch or GCS directly.
 *
 * <p>P10-T01 ships the skeleton: {@link com.gotham.newsmediabrowser.datagen.DatagenApplication}
 * (CLI + {@code --help}) and {@link com.gotham.newsmediabrowser.datagen.DatagenConfig} (layered
 * configuration). The generative helper clients (Qwen / SDXL-Turbo / Kokoro / Wan) and the
 * orchestrator arrive in P10-T03…T04.
 */
package com.gotham.newsmediabrowser.datagen;
