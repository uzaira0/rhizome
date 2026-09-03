package com.geekbeast.rhizome.emails;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.geekbeast.rhizome.pods.hazelcast.SelfRegisteringStreamSerializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jodd.mail.CommonEmail;
import jodd.mail.Email;
import jodd.mail.EmailAddress;
import jodd.mail.EmailAttachment;
import jodd.mail.EmailMessage;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public abstract class AbstractEmailStreamSerializer implements SelfRegisteringStreamSerializer<Email> {

    @Override
    public void write( ObjectDataOutput out, Email object ) throws IOException {
        serialize( out, object );
    }

    @Override
    public Email read( ObjectDataInput in ) throws IOException {
        return deserialize( in );
    }

    @Override
    public abstract int getTypeId();

    @Override
    public void destroy() {

    }

    @Override
    public Class<Email> getClazz() {
        return Email.class;
    }

    public static void serialize( ObjectDataOutput out, Email object ) throws IOException {
        serializeMailAddresses( out, object.from() );
        serializeMailAddresses( out, object.to() );
        serializeMailAddresses( out, object.cc() );
        serializeMailAddresses( out, object.bcc() );
        serializeMailAddresses( out, object.replyTo() );
        out.writeString( object.subject() );
        out.writeString( object.subjectEncoding() );
        out.writeInt( object.priority() );
        Date sentDate = object.sentDate();
        boolean hasSentDate = ( sentDate != null );
        out.writeBoolean( hasSentDate );
        if ( hasSentDate ) {
            out.writeLong( object.sentDate().getTime() );
        }
        serializeEmailMessages( out, object.messages() );
        serializeAttachments( out, object.attachments() );
        serializeHeaders( out, getHeaders( object ) );
    }

    public static Email deserialize( ObjectDataInput in ) throws IOException {
        Email email = new Email();
        EmailAddress from = deserializeMailAddresses( in )[ 0 ];
        EmailAddress[] tos = deserializeMailAddresses( in );
        EmailAddress[] ccs = deserializeMailAddresses( in );
        EmailAddress[] bccs = deserializeMailAddresses( in );
        EmailAddress[] replyTo = deserializeMailAddresses( in );

        String subject = in.readString();
        String subjectEncoding = in.readString();
        int priority = in.readInt();
        boolean hasSentDate = in.readBoolean();

        if ( hasSentDate ) {
            long sentTime = in.readLong();
            email.sentDate( new Date( sentTime ) ); // This is here to avoid double if check & auto-boxing
        }

        List<EmailMessage> messages = deserializeEmailMessages( in );
        for ( EmailMessage message : messages ) {
            email.message( message );
        }

        List<EmailAttachment<?>> attachments = deserializeAttachments( in );
        for ( EmailAttachment<?> attachment : attachments ) {
            email.attachment( attachment );
        }

        Map<String, String> headers = deserializeHeaders( in );
        for ( Map.Entry<String, String> entry : headers.entrySet() ) {
            email.header( entry.getKey(), entry.getValue() );
        }

        email.from( from );
        email.to( tos );
        email.cc( ccs );
        email.bcc( bccs );
        email.replyTo( replyTo );
        email.subject( subject, subjectEncoding );
        email.priority( priority );

        return email;
    }

    public static void serializeMailAddresses( ObjectDataOutput out, EmailAddress... addresses ) throws IOException {
        out.writeInt( addresses.length );
        for ( EmailAddress address : addresses ) {
            out.writeString( address.getEmail() );

            String personalName = address.getPersonalName();
            boolean hasPersonalName = StringUtils.isNotBlank( personalName );
            out.writeBoolean( hasPersonalName );
            if ( hasPersonalName ) {
                out.writeString( address.getPersonalName() );
            }
        }
    }

    public static EmailAddress[] deserializeMailAddresses( ObjectDataInput in ) throws IOException {
        int length = in.readInt();
        EmailAddress[] addresses = new EmailAddress[ length ];
        for ( int i = 0; i < length; ++i ) {
            String email = Objects.requireNonNull(in.readString());
            boolean hasPersonalName = in.readBoolean();
            if ( hasPersonalName ) {
                String personalName = in.readString();
                addresses[ i ] = new EmailAddress( personalName, email );
            } else {
                addresses[ i ] = EmailAddress.of( email );
            }
        }
        return addresses;
    }

    public static void serializeEmailMessages( ObjectDataOutput out, List<EmailMessage> messages ) throws IOException {
        out.writeInt( messages.size() );
        for ( EmailMessage message : messages ) {
            out.writeString( message.getContent() );
            out.writeString( message.getEncoding() );
            out.writeString( message.getMimeType() );
        }
    }

    public static List<EmailMessage> deserializeEmailMessages( ObjectDataInput in ) throws IOException {
        int size = in.readInt();
        List<EmailMessage> messages = new ArrayList<>( size );
        for ( int i = 0; i < size; ++i ) {
            String content = in.readString();
            String encoding = in.readString();
            String mimeType = in.readString();
            messages.add( new EmailMessage( content, mimeType, encoding ) );
        }
        return messages;
    }

    public static void serializeAttachments( ObjectDataOutput out, List<EmailAttachment<?>> attachments )
            throws IOException {
        if ( attachments == null ) {
            out.writeInt( 0 );
            return;
        }
        out.writeInt( attachments.size() );
        for ( EmailAttachment<?> attachment : attachments ) {
            out.writeUTF( attachment.getName() != null ? attachment.getName() : "" );
            out.writeUTF( attachment.getContentType() != null ? attachment.getContentType() : "application/octet-stream" );
            byte[] content = attachment.toByteArray();
            out.writeInt( content != null ? content.length : 0 );
            if ( content != null && content.length > 0 ) {
                out.write( content );
            }
        }
    }

    public static List<EmailAttachment<?>> deserializeAttachments( ObjectDataInput in ) throws IOException {
        int count = in.readInt();
        List<EmailAttachment<?>> attachments = new ArrayList<>( count );
        for ( int i = 0; i < count; ++i ) {
            String name = in.readString();
            String contentType = in.readString();
            int length = in.readInt();
            byte[] content = new byte[ length ];
            if ( length > 0 ) {
                in.readFully( content );
            }
            attachments.add( EmailAttachment.with().name( name ).content( content, contentType ).buildByteArrayDataSource() );
        }
        return attachments;
    }

    @SuppressWarnings( "unchecked" )
    private static Map<String, String> getHeaders( Email email ) {
        try {
            Method headersMethod = CommonEmail.class.getDeclaredMethod( "headers" );
            headersMethod.setAccessible( true );
            return (Map<String, String>) headersMethod.invoke( email );
        } catch ( NoSuchMethodException | IllegalAccessException | InvocationTargetException e ) {
            return java.util.Collections.emptyMap();
        }
    }

    public static void serializeHeaders( ObjectDataOutput out, Map<String, String> headers ) throws IOException {
        if ( headers == null ) {
            out.writeInt( 0 );
            return;
        }
        out.writeInt( headers.size() );
        for ( Map.Entry<String, String> entry : headers.entrySet() ) {
            out.writeUTF( entry.getKey() );
            out.writeUTF( entry.getValue() != null ? entry.getValue() : "" );
        }
    }

    public static Map<String, String> deserializeHeaders( ObjectDataInput in ) throws IOException {
        int count = in.readInt();
        Map<String, String> headers = new java.util.LinkedHashMap<>( count );
        for ( int i = 0; i < count; ++i ) {
            String key = in.readString();
            String value = in.readString();
            headers.put( key, value );
        }
        return headers;
    }

}
