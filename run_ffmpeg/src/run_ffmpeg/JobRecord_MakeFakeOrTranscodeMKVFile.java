package run_ffmpeg;

public class JobRecord_MakeFakeOrTranscodeMKVFile
{
	public String mkvLongPath = null ;
	public String mp4LongPath = null ;
	public String movieOrShowName_id = null ;
	public String movieOrShowName = null ;
	public String mkvFileName = null ;
	public String fileName = null ;
	public String missingMKVFile = null ;
	public String missingMP4File = null ;	
	
	public String toString()
	{
		String retMe = "{"
				+ mkvLongPath +","
				+ mp4LongPath + ","
				+ movieOrShowName_id + ","
				+ movieOrShowName + ","
				+ mkvFileName + ","
				+ fileName + ","
				+ missingMKVFile + ","
				+ missingMP4File
				+ "}" ;
		return retMe ;
	}

	public String getFileName() {
		return fileName;
	}

	public String getMissingMKVFile() {
		return missingMKVFile;
	}

	public String getMissingMP4File() {
		return missingMP4File;
	}

	public String getMKVFileName() {
		return mkvFileName;
	}

	public String getMKVLongPath() {
		return mkvLongPath;
	}

	public String getMovieOrShowName() {
		return movieOrShowName;
	}

	public String getMovieOrShowName_id() {
		return movieOrShowName_id;
	}

	public String getMP4LongPath() {
		return mp4LongPath;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public void setMissingMKVFile(String missingMKVFile) {
		this.missingMKVFile = missingMKVFile;
	}

	public void setMissingMP4File(String missingMP4File) {
		this.missingMP4File = missingMP4File;
	}

	public void setMKVFileName(String mkvFileName) {
		this.mkvFileName = mkvFileName;
	}

	public void setMKVLongPath(String mkvLongPath) {
		this.mkvLongPath = mkvLongPath;
	}

	public void setMovieOrShowName(String movieOrShowName) {
		this.movieOrShowName = movieOrShowName;
	}

	public void setMovieOrShowName_id(String movieOrShowName_id) {
		this.movieOrShowName_id = movieOrShowName_id;
	}

	public void setMP4LongPath(String mp4LongPath) {
		this.mp4LongPath = mp4LongPath;
	}
}
