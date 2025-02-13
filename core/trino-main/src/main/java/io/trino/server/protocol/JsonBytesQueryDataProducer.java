/*
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
package io.trino.server.protocol;

import com.fasterxml.jackson.core.JsonGenerator;
import io.trino.Session;
import io.trino.client.QueryData;
import io.trino.server.ExternalUriInfo;
import io.trino.server.protocol.JsonEncodingUtils.TypeEncoder;
import io.trino.server.protocol.spooling.QueryDataProducer;
import io.trino.spi.TrinoException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Consumer;

import static io.trino.plugin.base.util.JsonUtils.jsonFactory;
import static io.trino.server.protocol.JsonEncodingUtils.createTypeEncoders;
import static io.trino.server.protocol.JsonEncodingUtils.writePagesToJsonGenerator;
import static java.util.Objects.requireNonNull;

public class JsonBytesQueryDataProducer
        implements QueryDataProducer
{
    private TypeEncoder[] typeEncoders;
    private int[] sourcePageChannels;

    @Override
    public QueryData produce(ExternalUriInfo uriInfo, Session session, QueryResultRows rows, Consumer<TrinoException> throwableConsumer)
    {
        if (rows.isEmpty()) {
            return null;
        }

        List<OutputColumn> columns = rows.getOutputColumns()
                .orElseThrow(() -> new IllegalStateException("Data present without columns"));

        if (typeEncoders == null) {
            typeEncoders = createTypeEncoders(session, columns);
            sourcePageChannels = requireNonNull(columns, "columns is null").stream()
                    .mapToInt(OutputColumn::sourcePageChannel)
                    .toArray();
        }

        // Write to a buffer so we can capture and propagate the exception
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); JsonGenerator generator = jsonFactory().createGenerator(outputStream)) {
            writePagesToJsonGenerator(session.toConnectorSession(), generator, typeEncoders, sourcePageChannels, rows.getPages());
            return new JsonBytesQueryData(outputStream.toByteArray());
        }
        catch (TrinoException e) {
            return null;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
