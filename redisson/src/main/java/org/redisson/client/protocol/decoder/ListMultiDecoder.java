/**
 * Copyright 2018 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.client.protocol.decoder;

import java.io.IOException;
import java.util.List;

import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

import io.netty.buffer.ByteBuf;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> type
 */
public class ListMultiDecoder<T> implements MultiDecoder<Object> {

    public static final Decoder<Object> RESET = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            return null;
        }
    };
    
    private final MultiDecoder<?>[] decoders;
    
    public static class NestedDecoderState implements DecoderState {

        int index = -1;
        int partsIndex = -1;
        
        public NestedDecoderState() {
        }
        
        public NestedDecoderState(int index) {
            super();
            this.index = index;
        }

        public void resetIndex() {
            index = 0;
        }
        
        public void resetPartsIndex() {
            partsIndex = -1;
        }
        
        public int incPartsIndex() {
            return ++partsIndex;
        }
        
        public int getPartsIndex() {
            return partsIndex;
        }
        
        public int incIndex() {
            return ++index;
        }
        
        public int getIndex() {
            return index;
        }

        @Override
        public DecoderState copy() {
            return new NestedDecoderState(index);
        }

        @Override
        public String toString() {
            return "NestedDecoderState [index=" + index + "]";
        }
        
    }
    
    protected final NestedDecoderState getDecoder(State state) {
        NestedDecoderState ds = state.getDecoderState();
        if (ds == null) {
            ds = new NestedDecoderState();
            state.setDecoderState(ds);
        }
        return ds;
    }
    
    public ListMultiDecoder(MultiDecoder<?> ... decoders) {
        this.decoders = decoders;
    }

    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        if (paramNum == 0) {
            NestedDecoderState s = getDecoder(state);
            s.incIndex();
            s.resetPartsIndex();
        }

        int index = getDecoder(state).getIndex();
        if (index == -1) {
            getDecoder(state).resetIndex();
            index = 0;
        }
        Decoder<Object> decoder = decoders[index].getDecoder(paramNum, state);
        if (decoder == RESET) {
            NestedDecoderState s = getDecoder(state);
            s.resetIndex();
            int ind = s.getIndex();
            return decoders[ind].getDecoder(paramNum, state);
        }
        return decoder;
    }
    
    @Override
    public Object decode(List<Object> parts, State state) {
        NestedDecoderState s = getDecoder(state);
        int index = s.getIndex();
        index += s.incPartsIndex();
        
        if (index == -1) {
            return decoders[decoders.length-1].decode(parts, state);
        }
        
        Object res = decoders[index].decode(parts, state);
        if (res == null) {
            index = s.incIndex() + s.getPartsIndex();
            return decoders[index].decode(parts, state);
        }
        return res;
    }
    
}
