package run_ffmpeg;

import org.bson.types.ObjectId;

public class JobRecord_UpdateCorrelatedFile
{
	public String fileNameWithPath = null ;
	public ObjectId movieAndShowInfo_id = null ;
	public ObjectId ffmpegProbeResult_id = null ;
	
	public JobRecord_UpdateCorrelatedFile()
	{}
	
	public JobRecord_UpdateCorrelatedFile( String fileNameWithPath, ObjectId movieAndShowInfo_id, ObjectId ffmpegProbeResult_id )
	{
		this.fileNameWithPath = fileNameWithPath ;
		this.movieAndShowInfo_id = movieAndShowInfo_id ;
		this.ffmpegProbeResult_id = ffmpegProbeResult_id ;
	}
	
	public String toString()
	{
		final String retMe =
				"{ fileNameWithPath: " + getFileNameWithPath()
				+ ", movieAndshowInfo_id: " + getMovieAndShowInfo_id().toString()
				+ ", ffmpegProbeResult_id: " + getFfmpegProbeResult_id().toString()
				+ "}" ;
		return retMe ;
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

	public ObjectId getFfmpegProbeResult_id() {
		return ffmpegProbeResult_id;
	}

	public void setFfmpegProbeResult_id(ObjectId ffmpegProbeResult_id) {
		this.ffmpegProbeResult_id = ffmpegProbeResult_id;
	}

}
