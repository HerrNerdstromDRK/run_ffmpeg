package run_ffmpeg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

public class WorkflowStageThread_TranscodeMKVFiles extends WorkflowStageThread
{
	private transient MongoCollection< JobRecord_MakeFakeOrTranscodeMKVFile > jobRecord_TranscodeMKVFilesInfoCollection = null ;
	private transient MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = null ;
	private final String mp4WorkingDirectory = "D:\\temp" ;

	public WorkflowStageThread_TranscodeMKVFiles( final String threadName,
			Logger log,
			Common common,
			MoviesAndShowsMongoDB masMDB )
	{
		super( threadName, log, common, masMDB ) ;
//		jobRecord_TranscodeMKVFilesInfoCollection = masMDB.getJobRecord_TranscodeMKVFileInfoCollection() ;
		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
	}

	@Override
	public void doAction()
	{
//		// Get the jobrecord from the database.
//		// No real way to do this without deleting jobs.
//		// If I don't delete the job, then this thread will infinitely spin on this one item.
//		JobRecord_MakeFakeOrTranscodeMKVFile theJob = jobRecord_TranscodeMKVFilesInfoCollection.findOneAndDelete( null ) ;
//		if( null == theJob )
//		{
//			// Nothing to do.
//			return ;
//		}
//		log.info( "Got job: " + theJob ) ;
//
//		// Get the corresponding MovieAndShowInfo
//		Bson movieAndShowInfoIDFilter = Filters.eq( "_id", new ObjectId( theJob.getMovieOrShowName_id() ) ) ;
//		MovieAndShowInfo movieAndShowInfo = movieAndShowInfoCollection.find( movieAndShowInfoIDFilter ).first() ;
//		if( null == movieAndShowInfo )
//		{
//			log.warning( "Unable to find movieAndShowInfo: " + theJob.getMovieOrShowName_id().toString() ) ;
//			return ;
//		}
//		log.fine( "Found movieAndShowInfo: " + theJob.toString() );
//
//		// Setup the destination directory for the mp4 file
//		// mp4LongPath, if it exists, will NOT have the trailing path separator
//		String mp4FinalDirectory = theJob.getMP4LongPath() ;
//
//		// Setup some variables for use in this method.
//		final String finalOutputFileNameWithPath = finalOutputDirectory
//				+ common.getPathSeparator()
//				+ theJob.getFileName()
//				+ ".mp4" ;
//		log.info( "mp4FileNameWithPath: " + mp4FinalFileNameWithPath ) ;
//
//		final String mkvInputDirectory = theJob.getMKVLongPath() ;
//		final String mkvFinalDirectory = mkvInputDirectory ;
//
//		final String mkvInputFileNameWithPath = mkvInputDirectory
//				+ common.getPathSeparator()
//				+ theJob.getFileName()
//				+ ".mkv" ;
//		final File mkvInputFileWithPath = new File( mkvInputFileNameWithPath ) ;
//
//		TranscodeCommon tCommon = new TranscodeCommon( log, common ) ;
//
//		// Got the mkv file and mp4 file information.
//		// Execute the extract PGS.
//		TranscodeFile fileToTranscode = new TranscodeFile( mkvInputFileWithPath,
//				mkvInputDirectory,
//				getMP4WorkingDirectory(),
//				mp4FinalDirectory,
//				log ) ;

//		// Extract subtitle streams.
//		ExtractPGSFromMKVs extractPGSFromMKVs = new ExtractPGSFromMKVs() ;
//		// runOneFile() will also prune the .sup files
//		extractPGSFromMKVs.runOneFile( fileToTranscode ) ;
//
//		OCRSubtitle ocrSubtitle = new OCRSubtitle() ;
//		List< File > filesToOCR = common.getFilesInDirectoryByExtension( mkvInputDirectory, OCRSubtitle.getExtensionsToOCR() ) ;
//		for( File fileToOCR : filesToOCR )
//		{
//			ocrSubtitle.doOCRFile( fileToOCR ) ;
//			if( !common.getTestMode() )
//			{
//				fileToOCR.delete() ;
//			}
//		}
//
//		// Transcode the file.
//		// Force a refresh of supporting files (.srt) by creating a new TranscodeFile
//		fileToTranscode = new TranscodeFile( mkvInputFileWithPath,
//				mkvInputDirectory,
//				getMP4WorkingDirectory(),
//				mp4FinalDirectory,
//				log ) ;
//		FFmpegProbeResult mkvProbeResult = common.ffprobeFile( mkvInputFileWithPath, log ) ;
//		if( null == mkvProbeResult )
//		{
//			log.warning( "mkvProbeResult is null" ) ;
//		}
//		fileToTranscode.processFFmpegProbeResult( mkvProbeResult ) ;
//		fileToTranscode.makeDirectories() ;
//		fileToTranscode.setTranscodeInProgress();
//
//		boolean transcodeSucceeded = tCommon.transcodeFile( fileToTranscode ) ;
//		if( !transcodeSucceeded )
//		{
//			log.warning( "Transcode failed" ) ;
//			fileToTranscode.unSetTranscodeInProgress() ;
//			return ;
//		}
//		fileToTranscode.setTranscodeComplete() ;
//
//		// Move files to their destinations
//		// For now, only the mp4 file needs to move.
//		log.info( "Moving mp4 file from " + getMP4WorkingDirectory() + " to " + mp4FinalDirectory ) ;
//		if( !common.getTestMode() )
//		{
//			final String mp4FileNameInWorkingDirectory = fileToTranscode.getMP4OutputFileNameWithPath() ;
//			final String mp4FileNameInFinalDirectory = fileToTranscode.getMP4FinalFileNameWithPath() ;
//
//			try
//			{
//				Path temp = Files.move(
//						Paths.get( mp4FileNameInWorkingDirectory ),
//						Paths.get( mp4FileNameInFinalDirectory ) ) ;
//				if( temp != null )
//				{
//					log.info( "Move successful." ) ;
//				}
//				else
//				{
//					log.warning( "Move failed." ) ;
//				}
//			}
//			catch( Exception theException )
//			{
//				log.warning( "Exception: " + theException.toString() ) ;
//				return ;
//			}
//		}
//
//		// Update the probe information for this file
//		// Be sure to force the refresh.
//		ProbeDirectories pd = new ProbeDirectories() ;
//		FFmpegProbeResult mp4ProbeResult = pd.probeFileAndUpdateDB( new File( fileToTranscode.getMP4FinalFileNameWithPath() ), true ) ;
//
//		// Update the movie and show index
//		movieAndShowInfo.updateCorrelatedFile( mp4ProbeResult ) ;
//		movieAndShowInfo.makeReadyCorrelatedFilesList() ;
//		movieAndShowInfoCollection.replaceOne( movieAndShowInfoIDFilter,  movieAndShowInfo ) ;
	}

	public String getMP4WorkingDirectory()
	{
		return mp4WorkingDirectory ;
	}
}
