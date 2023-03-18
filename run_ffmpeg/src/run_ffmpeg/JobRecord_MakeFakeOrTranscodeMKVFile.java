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

	public String getMkvLongPath() {
		return mkvLongPath;
	}

	public void setMkvLongPath(String mkvLongPath) {
		this.mkvLongPath = mkvLongPath;
	}

	public String getMp4LongPath() {
		return mp4LongPath;
	}

	public void setMp4LongPath(String mp4LongPath) {
		this.mp4LongPath = mp4LongPath;
	}

	public String getMovieOrShowName_id() {
		return movieOrShowName_id;
	}

	public void setMovieOrShowName_id(String movieOrShowName_id) {
		this.movieOrShowName_id = movieOrShowName_id;
	}

	public String getMovieOrShowName() {
		return movieOrShowName;
	}

	public void setMovieOrShowName(String movieOrShowName) {
		this.movieOrShowName = movieOrShowName;
	}

	public String getMkvFileName() {
		return mkvFileName;
	}

	public void setMkvFileName(String mkvFileName) {
		this.mkvFileName = mkvFileName;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getMissingMKVFile() {
		return missingMKVFile;
	}

	public void setMissingMKVFile(String missingMKVFile) {
		this.missingMKVFile = missingMKVFile;
	}

	public String getMissingMP4File() {
		return missingMP4File;
	}

	public void setMissingMP4File(String missingMP4File) {
		this.missingMP4File = missingMP4File;
	}
}
