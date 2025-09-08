package run_ffmpeg;

import java.io.File;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

public class WorkflowStageThread_SubtitleTranscribe extends WorkflowStageThread
{
	protected transient MongoCollection< JobRecord_FileNameWithPath > createSRTWithTranscribeCollection = null ;
	protected transient OpenAIWhisper whisper = null ;

	public WorkflowStageThread_SubtitleTranscribe( final String threadName, Logger log, Common common, MoviesAndShowsMongoDB masMDB )
	{
		super( threadName, log, common, masMDB ) ;
		createSRTWithTranscribeCollection = masMDB.getAction_CreateSRTsWithTranscribeCollection() ;
		whisper = new OpenAIWhisper( log, common ) ;
	}

	/**
	 * Override this method. This method performs the work intended for subclass instances of this class.
	 * @return true if work completed false if no work completed.
	 */
	public boolean doAction()
	{
		final JobRecord_FileNameWithPath jobRecord = createSRTWithTranscribeCollection.findOneAndDelete( null ) ;
		if( null == jobRecord )
		{
			return false ;
		}
		setWorkInProgress( true ) ;
		final File inputWavFile = new File( jobRecord.getFileNameWithPath() ) ;

		// The output filename will default, as will the language file.
		// Generate an SRT via whisper ai.
		final String srtFileNameWithPath = inputWavFile.getAbsolutePath().replace( ".wav", ".en.srt" ) ;

		final File srtFile = new File( srtFileNameWithPath ) ;
		if( !srtFile.exists() )
		{
			// File does not already exist -- create it via transcription.
			final File srtFileWithWrongName = whisper.transcribeToSRT( inputWavFile ) ;

			// The whisperX AI outputs the srt file with the same name as the .wav file.
			// Need to change it to include ".en" in the file name
			if( srtFileWithWrongName != null )
			{
				srtFileWithWrongName.renameTo( srtFile ) ;
				
				// Delete the .wav file
				inputWavFile.delete() ;
			}
		}

		setWorkInProgress( false ) ;
		return true ;
	}
	
	@Override
	public String getUpdateString()
	{
		return "Database size: " + createSRTWithTranscribeCollection.countDocuments() ;
	}
}
