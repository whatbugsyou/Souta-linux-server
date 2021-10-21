touch init.sh
echo '#!/bin/bash
start=$1
end=$2
ip_pref=$3
iptables -t mangle -F OUTPUT # 清空OUTPUT规则
iptables -t nat -F POSTROUTING # 清空POSTROUTING规则
for ((i=$start; i <= $end  ; i++))
do
	big=`echo $ip_pref | awk -F. '{print $3}'`
	j=$[$big*1000+$i]
	/usr/sbin/useradd socks$j -u $j -M -s /dev/null
	iptables -t mangle -A OUTPUT -m owner --uid-owner $j -j MARK --set-mark $j
	iptables -t nat -A POSTROUTING -m mark --mark $j -j SNAT --to $ip_pref.$i
done
' >> init.sh