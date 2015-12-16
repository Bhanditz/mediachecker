package gr.ntua.image.mediachecker;

import org.json.JSONObject;

public final class VideoInfo {

	protected final int _Width, _Height, _BitRate;
	protected final long _Duration;
	protected final String _MimeType, _Codec;
	protected final double _FrameRate;

	public VideoInfo(int width, int height, String mimetype, String codec, long duration, double framerate, int bitrate) {
		_Width      = width;
		_Height     = height;
		_MimeType   = mimetype;
		_Codec      = codec;
		_Duration   = duration;
		_FrameRate  = framerate;
		_BitRate    = bitrate;
	}

	public int getWidth() {
		return _Width;
	}

	public int getHeight() {
		return _Height;
	}

	public long getDuration() {
		return _Duration;
	}

	public String getMimeType() {
		return _MimeType;
	}

	public String getCodec() {
		return _Codec;
	}

	public double getFrameRate() {
		return _FrameRate;
	}

	public String getResolution() {
		return _Width + "x" + _Height;
	}

	public int getBitRate() {
		return _BitRate;
	}

	public JSONObject toJSON() {
		JSONObject jo = new JSONObject();

		jo.put("width", _Width);
		jo.put("height", _Height);
		jo.put("mimetype", _MimeType);
		jo.put("codec", _Codec);
		jo.put("duration", _Duration);
		jo.put("framerate", _FrameRate);
		jo.put("bitrate", _BitRate);

		return jo;
	}
}