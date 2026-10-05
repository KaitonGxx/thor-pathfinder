# Presses one button of the Thor's controller N times through the kernel,
# the same path a thumb takes. Runs on the Thor, as the shell user.
# usage: sh inject.sh <event node> <scan code> <count>
#   e.g. sh inject.sh /dev/input/event9 317 60    (L3 on "Odin Controller")
dev=$1; code=$2; n=${3:-100}; i=0
while [ $i -lt $n ]; do
  sendevent $dev 1 $code 1; sendevent $dev 0 0 0
  sleep 0.04
  sendevent $dev 1 $code 0; sendevent $dev 0 0 0
  sleep 0.$(( 15 + RANDOM % 20 ))
  i=$((i+1))
done
