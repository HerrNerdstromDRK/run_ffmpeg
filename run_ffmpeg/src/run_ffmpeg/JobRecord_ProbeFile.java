package run_ffmpeg;

import org.bson.types.ObjectId;

public class JobRecord_ProbeFile
{
	public String fileNameWithPath = null ;
	public ObjectId movieAndShowInfo_id = null ;
	
	/// isFakeFile is true if this is a fake MKV file, i.e., one that I don't have for some
	/// reason but needs to be included in the probe list for consistency.
	public boolean isFakeFile = false ;
	
	public JobRecord_ProbeFile()
	{}
	
	public JobRecord_ProbeFile( final String fileNameWithPath )
	{
		this.fileNameWithPath = fileNameWithPath ;
	}
	
	public JobRecord_ProbeFile( final String fileNameWithPath, MovieAndShowInfo movieAndShowInfo, boolean isFakeFile )
	{
		this.fileNameWithPath = fileNameWithPath ;
		this.movieAndShowInfo_id = movieAndShowInfo._id ;
		this.isFakeFile = isFakeFile ;
	}
	
	public String toString()
	{
		return "{" + getFileNameWithPath()
				+ ", isFakeFile: " + isFakeFile()
				+ "}" ;
	}

	public String getFileNameWithPath() {
		return fileNameWithPath;
	}

	public void setFileNameWithPath(String fileNameWithPath) {
		this.fileNameWithPath = fileNameWithPath;
	}

	public ObjectId getMovieAndShowInfo_id() {
		return movieAndShowInfo_id;
	}

	public void setMovieAndShowInfo_id(ObjectId movieAndShowInfo_id) {
		this.movieAndShowInfo_id = movieAndShowInfo_id;
	}

	public boolean isFakeFile() {
		return isFakeFile;
	}

	public void setFakeFile(boolean isFakeFile) {
		this.isFakeFile = isFakeFile;
	}
}
