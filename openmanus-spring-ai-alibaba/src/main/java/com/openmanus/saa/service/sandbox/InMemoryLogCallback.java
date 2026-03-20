package com.openmanus.saa.service.sandbox;

import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.model.Frame;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class InMemoryLogCallback extends ResultCallbackTemplate<InMemoryLogCallback, Frame> {

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    @Override
    public void onNext(Frame item) {
        try {
            outputStream.write(item.getPayload());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] toByteArray() {
        return outputStream.toByteArray();
    }
}
