package com.soap.storm.trident;

import org.apache.storm.Config;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.trident.operation.TridentCollector;
import org.apache.storm.trident.spout.IBatchSpout;
import org.apache.storm.trident.testing.FixedBatchSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SentenceBatchSpout implements IBatchSpout {

    Fields fields;
    List<Object>[] outputs;
    int maxBatchSize;
    HashMap<Long, List<List<Object>>> batches = new HashMap<Long, List<List<Object>>>();

    public SentenceBatchSpout(Fields fields, int maxBatchSize, List<Object>... outputs) {
        this.fields = fields;
        this.outputs = outputs;
        this.maxBatchSize = maxBatchSize;
    }

    int index = 0;
    boolean cycle = false;

    public void setCycle(boolean cycle) {
        this.cycle = cycle;
    }

    @Override
    public void open(Map conf, TopologyContext context) {
        index = 0;
    }

    @Override
    public void emitBatch(long batchId, TridentCollector collector) {
        List<List<Object>> batch = this.batches.get(batchId);
        if(batch == null){
            batch = new ArrayList<List<Object>>();
            if(index>=outputs.length && cycle) {
                index = 0;
            }
            for(int i=0; index < outputs.length && i < maxBatchSize; index++, i++) {
                batch.add(outputs[index]);
            }
            this.batches.put(batchId, batch);
        }
        for(List<Object> list : batch){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            collector.emit(list);
        }
    }

    @Override
    public void ack(long batchId) {
        this.batches.remove(batchId);
    }

    @Override
    public void close() {
    }

    @Override
    public Map<String, Object> getComponentConfiguration() {
        Config conf = new Config();
        conf.setMaxTaskParallelism(1);
        return conf;
    }

    @Override
    public Fields getOutputFields() {
        return fields;
    }
}

