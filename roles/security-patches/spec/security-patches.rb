describe command('echo $LOG4J_FORMAT_MSG_NO_LOOKUPS') do
  its(:stdout) { should eq 'true' }
end
