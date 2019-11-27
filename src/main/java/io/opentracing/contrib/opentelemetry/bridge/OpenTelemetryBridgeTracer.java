/* Copyright 2019 The OpenTracing Authors
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

package io.opentracing.contrib.opentelemetry.bridge;

import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.exporters.inmemory.InMemorySpanExporter;
import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporters.logging.LoggingExporter;
import io.opentelemetry.opentracingshim.TraceShim;
import io.opentelemetry.sdk.distributedcontext.DistributedContextManagerSdk;
import io.opentelemetry.sdk.trace.TracerSdkFactory;
import io.opentelemetry.sdk.trace.export.SimpleSpansProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerFactory;

public class OpenTelemetryBridgeTracer implements TracerFactory {
  private static final String EXPORTER_PROP = "ot.otel.exporter";
  private static final String JAEGER_PROP = EXPORTER_PROP + ".jaeger";

  private static Long parseLong(final String str) {
    if (str == null)
      return null;

    try {
      return Long.parseLong(str);
    }
    catch (final NumberFormatException e) {
      return null;
    }
  }

  @Override
  public Tracer getTracer() {
    final String exporterProperty = System.getProperty(EXPORTER_PROP);
    final SpanExporter exporter;
    if ("jaeger".equals(exporterProperty)) {
      final String serviceName = System.getProperty(JAEGER_PROP + ".serviceName");
      if (serviceName == null)
        throw new IllegalArgumentException(JAEGER_PROP + ".serviceName=<SERVICE_NAME> is invalid: " + serviceName);

      final String address = System.getProperty(JAEGER_PROP + ".address");
      final int colon;
      final String host;
      final Long port;
      if (address == null || (colon = address.indexOf(':')) == -1 || (host = address.substring(0, colon)).length() == 0 || (port = parseLong(address.substring(colon + 1))) == null || 1 < port || port > 65535)
        throw new IllegalArgumentException(JAEGER_PROP + ".address=<HOST:PORT> is invalid: " + address);

      final JaegerGrpcSpanExporter.Builder builder = JaegerGrpcSpanExporter.newBuilder().setServiceName(serviceName);
      builder.setChannel(ManagedChannelBuilder.forAddress(host, port.intValue()).usePlaintext().build());

      final Long deadline = parseLong(System.getProperty(JAEGER_PROP + ".deadline"));
      if (deadline != null)
        builder.setDeadline(deadline);

      exporter = builder.build();
    }
    else if ("inmemory".equals(exporterProperty)) {
      exporter = InMemorySpanExporter.create();
    }
    else if ("logging".equals(exporterProperty)) {
      exporter = new LoggingExporter();
    }
    else if (exporterProperty != null) {
      throw new UnsupportedOperationException("Unsupported " + EXPORTER_PROP + "=" + exporterProperty);
    }
    else {
      return TraceShim.createTracerShim();
    }

    final boolean reportOnlySampled = Boolean.getBoolean(JAEGER_PROP + ".reportOnlySampled");
    final TracerSdkFactory factory = TracerSdkFactory.create();
    factory.addSpanProcessor(SimpleSpansProcessor.newBuilder(exporter).reportOnlySampled(reportOnlySampled).build());

    return TraceShim.createTracerShim(factory, new DistributedContextManagerSdk());
  }
}