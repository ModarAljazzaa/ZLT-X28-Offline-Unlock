#!/bin/sh
set -e

# Edit these values before serving this script to the modem.
# Use the normal credentials printed on the router label so they are memorable
# after a reset. Give the senior and super accounts different private values.
NORMAL_LOGIN_NAME='user'
NORMAL_LOGIN_PWD='CHANGE_ME_TO_ROUTER_PASSWORD'
SENIOR_LOGIN_NAME='root'
SENIOR_LOGIN_PWD='CHANGE_ME_TO_PRIVATE_PASSWORD'
SUPER_LOGIN_NAME='CHANGE_ME_TO_PRIVATE_NAME'
SUPER_LOGIN_PWD='CHANGE_ME_TO_PRIVATE_PASSWORD'

# The first optional argument is the laptop IP; the second is the HTTP port.
LAPTOP_IP="${1:-192.168.70.131}"
HTTP_PORT="${2:-8000}"

case "$NORMAL_LOGIN_PWD:$SENIOR_LOGIN_PWD:$SUPER_LOGIN_NAME:$SUPER_LOGIN_PWD" in
*CHANGE_ME*)
echo "Edit the login values at the top of x28.sh before running it."
exit 1
;;
esac

escape_config_value() {
printf '%s' "$1" | sed 's/[\\&|\"]/\\&/g'
}

set_config_value() {
key="$1"
value=$(escape_config_value "$2")
sed -i "s|^export ${key}=.*|export ${key}=\"${value}\"|" /mnt/data/etc/tzcfg/main_config
}

mount -o remount,rw /
wget "http://$LAPTOP_IP:$HTTP_PORT/x28.tgz" -O /tmp/x28.tgz
hash=$(md5sum /tmp/x28.tgz | awk '{print $1}')
if [ "$hash" = '1468b8686d86b34337c1ee0086a42a76' ]
then
rm -rf /mnt/data/etc/tzcfg/update_config
[ -e /mnt/data/etc/tzcfg/main_config.bk ] || cp /mnt/data/etc/tzcfg/main_config /mnt/data/etc/tzcfg/main_config.bk
[ -e /tzwww/cgi-bin/http.cgi.bk ] || cp /tzwww/cgi-bin/http.cgi /tzwww/cgi-bin/http.cgi.bk
[ -e /usr/bin/mtk_netagent.bk ] || cp /usr/bin/mtk_netagent /usr/bin/mtk_netagent.bk
pids=$(ps | grep '[m]tk_netagent' | awk '{print $1}')
[ -z "$pids" ] || kill $pids
tar -xzvf /tmp/x28.tgz -C /
set_config_value SYS_USER_LOGIN_NAME "$NORMAL_LOGIN_NAME"
set_config_value SYS_USER_LOGIN_PWD "$NORMAL_LOGIN_PWD"
set_config_value SYS_SENIOR_LOGIN_NAME "$SENIOR_LOGIN_NAME"
set_config_value SYS_SENIOR_LOGIN_PWD "$SENIOR_LOGIN_PWD"
set_config_value SYS_SUPER_LOGIN_NAME "$SUPER_LOGIN_NAME"
set_config_value SYS_SUPER_LOGIN_PWD "$SUPER_LOGIN_PWD"

# Disable TR-069/CWMP remote management and clear its remote endpoints.
set_config_value USR_TR069_SW "0"
set_config_value USR_TR069_PERIODIC_SW "0"
set_config_value USR_TR069_UPGRADE_AUTO_SW "0"
set_config_value SYS_TR069_UPGRADE_PROMPT_SW "0"
set_config_value USR_TR069_LONG_CONNECT_ENABLE "0"
set_config_value USR_TR069_ACS_URL ""
set_config_value USR_TR069_ACS_USERNAME ""
set_config_value USR_TR069_ACS_PWD ""
set_config_value USR_TR069_CPE_USERNAME ""
set_config_value USR_TR069_CPE_PWD ""
set_config_value USR_TR069_SERVER_IP ""
set_config_value USR_TR069_SERVER_PORT ""
set_config_value USR_TR069_CHECK_VERSION_ADDR ""
/usr/bin/mtk_netagent &
sleep 3
sync
reboot
exit
else
echo "Archive checksum mismatch: $hash"
exit
fi
