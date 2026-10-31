# Minimal OpenSSH SFTP server for integration and end-to-end tests, native on amd64 and arm64.
# One customer account with inbound, inbound-instant, processing, processed, outbound and staging directories in its home directory.
FROM alpine:3.22

ARG SFTP_USER
ARG SFTP_PASSWORD

RUN apk add --no-cache openssh-server openssh-sftp-server \
    && ssh-keygen -A \
    && adduser -D -u 1001 "$SFTP_USER" \
    && echo "$SFTP_USER:$SFTP_PASSWORD" | chpasswd \
    && mkdir -p "/home/$SFTP_USER/inbound" "/home/$SFTP_USER/inbound-instant" "/home/$SFTP_USER/processing" "/home/$SFTP_USER/processed" "/home/$SFTP_USER/outbound" "/home/$SFTP_USER/staging" \
    && chown -R "$SFTP_USER:$SFTP_USER" "/home/$SFTP_USER" \
    && printf 'PasswordAuthentication yes\nSubsystem sftp internal-sftp\nForceCommand internal-sftp\n' \
       > /etc/ssh/sshd_config.d/sftp.conf

EXPOSE 22
CMD ["/usr/sbin/sshd", "-D", "-e"]
