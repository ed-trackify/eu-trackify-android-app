package common;

import org.apache.http.entity.InputStreamEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CountingInputStreamEntity extends InputStreamEntity {

	private IUploadListener listener;
	private long length;

	public CountingInputStreamEntity(InputStream instream, long length) {
		super(instream, length);
		this.length = length;
	}

	public void setUploadListener(IUploadListener listener) {
		this.listener = listener;
	}

	@Override
	public void writeTo(OutputStream outstream) throws IOException {
		super.writeTo(new CountingOutputStream(outstream));
	}

	class CountingOutputStream extends OutputStream {
		private long counter = 0l;

		private OutputStream outputStream;

		public CountingOutputStream(OutputStream outputStream) {
			this.outputStream = outputStream;
		}

		@Override
		public void write(byte[] buffer, int offset, int count) throws IOException {
			this.outputStream.write(buffer, offset, count);
			counter += count;
			if (listener != null) {
				int percent = (int) ((counter * 100) / length);
				listener.OnProgressChanged(percent);
			}
		}

		@Override
		public void write(int oneByte) throws IOException {

		}
	}

	public interface IUploadListener {
		public void OnProgressChanged(int percent);
	}
}
