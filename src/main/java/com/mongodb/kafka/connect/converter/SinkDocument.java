/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Original Work: Apache License, Version 2.0, Copyright 2017 Hans-Peter Grahsl.
 */

package com.mongodb.kafka.connect.converter;

import java.util.Optional;

import org.bson.BsonDocument;

public final class SinkDocument implements Cloneable {
    private final Optional<BsonDocument> keyDoc;
    private final Optional<BsonDocument> valueDoc;

    public SinkDocument(final BsonDocument keyDoc, final BsonDocument valueDoc) {
        this.keyDoc = Optional.ofNullable(keyDoc);
        this.valueDoc = Optional.ofNullable(valueDoc);
    }

    public Optional<BsonDocument> getKeyDoc() {
        return keyDoc;
    }

    public Optional<BsonDocument> getValueDoc() {
        return valueDoc;
    }

    @Override
    public SinkDocument clone() {
        BsonDocument kd = keyDoc.map(BsonDocument::clone).orElse(null);
        BsonDocument vd = valueDoc.map(BsonDocument::clone).orElse(null);
        return new SinkDocument(kd, vd);
    }

}