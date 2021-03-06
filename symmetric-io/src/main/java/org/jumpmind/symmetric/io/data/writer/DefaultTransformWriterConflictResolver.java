/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.io.data.writer;

import java.util.List;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.transform.TransformedData;

public class DefaultTransformWriterConflictResolver extends DefaultDatabaseWriterConflictResolver {

    protected TransformWriter transformWriter;

    public DefaultTransformWriterConflictResolver(TransformWriter transformWriter) {
        this.transformWriter = transformWriter;
    }

    @Override
    protected void performFallbackToInsert(AbstractDatabaseWriter writer, CsvData data, Conflict conflict, boolean retransform) {
        TransformedData transformedData = data.getAttribute(TransformedData.class.getName());
        if (transformedData != null && retransform) {
            List<TransformedData> newlyTransformedDatas = transformWriter.transform(
                    DataEventType.INSERT, writer.getContext(), transformedData.getTransformation(),
                    transformedData.getSourceKeyValues(), transformedData.getOldSourceValues(),
                    transformedData.getSourceValues());
            for (TransformedData newlyTransformedData : newlyTransformedDatas) {
                if (newlyTransformedData.hasSameKeyValues(transformedData.getKeyValues())
                        || newlyTransformedData.isGeneratedIdentityNeeded()) {
                    Table table = newlyTransformedData.buildTargetTable();
                    CsvData newData = newlyTransformedData.buildTargetCsvData();
                    if (newlyTransformedData.isGeneratedIdentityNeeded()) {
                        if (log.isDebugEnabled()) {
                            log.debug("Enabling generation of identity for {}",
                                    newlyTransformedData.getTableName());
                        }
                        writer.allowInsertIntoAutoIncrementColumns(false, table);
                    } else if (table.hasAutoIncrementColumn()) {
                        writer.allowInsertIntoAutoIncrementColumns(true, table);
                    }

                    writer.start(table);
                    writer.write(newData, true);
                    writer.end(table);
                }
            }
        } else {
            super.performFallbackToInsert(writer, data, conflict, retransform);
        }
    }

    @Override
    protected void performFallbackToUpdate(AbstractDatabaseWriter writer, CsvData data, Conflict conflict, boolean retransform) {
        TransformedData transformedData = data.getAttribute(TransformedData.class.getName());
        if (transformedData != null && retransform) {
            List<TransformedData> newlyTransformedDatas = transformWriter.transform(
                    DataEventType.UPDATE, writer.getContext(), transformedData.getTransformation(),
                    transformedData.getSourceKeyValues(), transformedData.getOldSourceValues(),
                    transformedData.getSourceValues());
            for (TransformedData newlyTransformedData : newlyTransformedDatas) {
                if (newlyTransformedData.hasSameKeyValues(transformedData.getKeyValues())) {
                    Table table = newlyTransformedData.buildTargetTable();
                    writer.start(table);
                    writer.write(newlyTransformedData.buildTargetCsvData(), true);
                    writer.end(table);
                }
            }
        } else {
            super.performFallbackToUpdate(writer, data, conflict, retransform);
        }
    }

}
